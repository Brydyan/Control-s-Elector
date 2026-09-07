# Design — T-BE-14 · Storage de imágenes de acta

---

## D1 — El acta guarda la clave S3, no la URL prefirmada

**Decisión**: `acta.storageUrl` almacena la **clave** del objeto
(`actas/{jrvId}/{cargoId}/{uuid}.webp`). La URL prefirmada se genera al leer, no se persiste.

**Alternativa descartada — guardar la URL prefirmada, como sugiere el nombre del campo.**
Una presigned URL caduca en 15 minutos. Guardarla produce una columna que está mal casi todo
el tiempo: el 99,9 % de las veces que alguien la lea, ya expiró. Es peor que un campo vacío,
porque parece un dato válido.

**Consecuencia**: el nombre `storageUrl` pasa a mentir sobre su contenido. O se renombra a
`storageKey` en una migración, o queda un comentario en la entidad diciendo exactamente qué
guarda. **Un comentario que miente es peor que ninguno** — y aquí el nombre del campo ya es
el comentario. Preferible renombrar.

---

## D2 — La compensación de S3 tiene que ocurrir donde el commit ya pasó

**Decisión**: la limpieza del objeto en S3 tras un fallo de base de datos se engancha al
**final de la transacción**, no a un `try/catch` alrededor de `repository.save(...)`.

**Por qué el patrón actual no funciona.** `EvidenciaService.upload` está anotado
`@Transactional` y hace:

```java
try {
    return repository.save(evidencia);
} catch (RuntimeException dbError) {
    s3.delete(s3Key);          // ← compensación
    throw dbError;
}
```

Con JPA, `save()` sobre una entidad nueva **no ejecuta el INSERT**: lo encola. El flush y el
commit ocurren cuando el método retorna y Spring cierra la transacción. Una violación de
constraint —un `sha256` duplicado, una FK inválida— explota **fuera** de ese `catch`. El
objeto queda huérfano en S3 mientras el comentario del archivo (líneas 26-29) afirma que se
limpia.

Es el patrón §4.3 del playbook: el comentario promete la garantía contraria a la del código.

**Alternativa descartada — dejarlo y añadir un batch de limpieza de huérfanos.** El propio
archivo ya lo menciona como plan («un batch de limpieza lo recogerá después»), pero ese batch
no existe. Una compensación diferida a un proceso inexistente es una compensación imaginaria.

**Cómo se verifica**: forzar una violación de constraint en el commit y comprobar con
`s3.exists(key)` que el objeto **no** quedó. Si queda, el arreglo no cerró nada.

---

## D3 — Los tests de storage corren contra MinIO real, no contra un doble

**Decisión**: los tests que verifican la compensación usan un contenedor MinIO
(Testcontainers), no un mock de `S3StorageService`.

**Alternativa descartada — mockear `S3StorageService`.** Es más rápido y no prueba lo que
hace falta. Con un mock, `s3.exists(key)` devuelve lo que el test le dijo que devolviera: la
aserción comprueba que el test está bien programado, no que el objeto se borró. Es el patrón
§4.1 —cada lado probado con el otro simulado— aplicado justo a la frontera que importa.

`compose.yaml` ya levanta RustFS (MinIO compatible) en dev y `pom.xml` ya trae Testcontainers.
El coste es un contenedor más por suite.

**Alternativa descartada — probar sólo contra el RustFS de `compose.yaml`.** Ata la suite a
que alguien haya levantado el compose, y en CI no está. Es §4.5: comportamiento que depende
de dónde corre.

---

## D4 — `delete` deja de tragarse el error de S3

**Decisión**: si el borrado en S3 falla, la operación **no** devuelve éxito.

**Alternativa descartada — mantener el comportamiento actual** (`log.error` y continuar,
`EvidenciaService:150-157`). El comentario lo justifica con «el FE ya recibió la confirmación
de borrado», pero eso describe el problema, no lo resuelve: el frontend afirma al usuario que
la evidencia se borró mientras la foto del acta sigue accesible en el bucket. En un sistema
de observación electoral, «borré esa foto» tiene que ser cierto.

**Consecuencia aceptada**: el borrado pasa a poder fallar, y el frontend tiene que
manejarlo. Es un contrato más honesto que el actual.

---

## D5 — La auditoría entra en este change, no en uno posterior

**Decisión**: `upload` y `delete` —de acta y de evidencia— registran en `AuditLogService`.

**Motivo**: `CLAUDE.md` fija el flujo `API → AuditLogService → JPA → PG` para toda acción
importante. Subir la foto de un acta y borrarla lo son: son las dos operaciones que pueden
destruir la prueba física de un resultado.

**Alternativa descartada — abrir un change de auditoría transversal.** Suena más ordenado y
deja el hueco abierto mientras tanto. Este change ya está tocando esos dos métodos: añadir la
llamada aquí cuesta minutos, y separarla garantiza que se quede sin hacer.
