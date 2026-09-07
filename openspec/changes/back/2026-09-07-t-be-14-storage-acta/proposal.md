# Proposal — T-BE-14 · Cerrar el storage de imágenes de acta

Origen: `documents/task-control/backendtask/T-BE-14.md` · Área: back · Fecha: 2026-09-07

---

## Qué se construye

El tramo que falta entre una foto de acta y su fila en la base: hoy
`POST /api/v1/observer/actas/{actaId}/imagen` devuelve **`501 NOT_IMPLEMENTED`**.

## Por qué el estado declarado no es el estado real

`T-BE-14.md:3` dice **«✅ COMPLETADO — S3StorageService ya implementado»**.
`documents/task-control/backend.md:218` dice **TODO**.
`ObserverController:191-196` tiene:

```java
// TODO(T-BE-14): subir a Supabase Storage y persistir Acta.storageUrl.
return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
```

Las dos afirmaciones son parcialmente ciertas, y por eso nadie lo detectó:

| Pieza | Estado real (verificado 2026-09-07) |
|---|---|
| `S3StorageService` (`upload`, `delete`, `exists`, `presignDownloadUrl`) | **existe y funciona** |
| Ruta de **evidencia**: `POST /api/v1/evidencias` → S3 + fila en `evidencia` | **existe** |
| Ruta de **acta**: `POST .../actas/{id}/imagen` → `acta.storageUrl` | **no existe. Devuelve 501** |

T-BE-14 se dio por cerrada mirando `S3StorageService`. La pieza que falta no es el servicio
de storage: es su uso desde el flujo de actas.

## Alcance

- Implementar `POST /api/v1/observer/actas/{actaId}/imagen`: recibe el multipart, valida,
  sube por `S3StorageService` y persiste la referencia en el acta.
- Decidir y dejar escrito qué guarda `acta.storageUrl` (ver `design.md` D1).
- Auditar el upload y el fallo de upload, que hoy no se auditan en **ninguna** de las dos rutas.
- Tests con S3 real de test (Testcontainers MinIO) o doble, según `design.md` D3.

## Fuera de alcance

- **No** se reescribe `S3StorageService`. Funciona.
- **No** se implementa la resiliencia offline (IndexedDB, Private Storage): es cliente, T-FE-06.
- **No** se implementa la compresión WebP. `CLAUDE.md` la sitúa en el cliente (Canvas API);
  el backend valida el resultado, no lo produce.
- **No** se tocan las observaciones de evidencia: es T-BE-16.

## Huecos que este change destapa y hay que decidir

1. **El upload de evidencia no se audita.** `EvidenciaService.upload` y `delete` no llaman a
   `AuditLogService`. `CLAUDE.md` dice que toda acción importante queda registrada. Borrar
   una evidencia —que destruye un objeto en S3— no deja rastro.
2. **La compensación de S3 promete más de lo que puede cumplir.**
   `EvidenciaService.upload` es `@Transactional` y su `catch (RuntimeException dbError)`
   rodea `repository.save(...)`. Con JPA, el flush y el commit ocurren **después** de que el
   método retorna: una violación de constraint en el commit **no** dispara ese `catch`, y el
   objeto queda huérfano en S3 pese al comentario que afirma lo contrario.
3. **`delete` se traga el error de S3** (`log.error` y sigue). El cliente recibe éxito y el
   objeto sigue en el bucket.
4. **La expiración de la presigned URL no coincide con lo documentado**: el código usa
   15 minutos (`PRESIGN_EXPIRY`), `T-BE-14.md:92` dice 24 horas.

Los cuatro se tratan aquí porque tocan el mismo servicio. Si se separan, se arreglan a medias.

## Criterio de cierre

1. `POST /api/v1/observer/actas/{actaId}/imagen` devuelve `201`, no `501`, y el acta queda
   con su referencia persistida.
2. Un fallo de S3 no deja el acta con una referencia que no se puede resolver.
3. Un fallo de base de datos no deja un objeto huérfano en S3 — **demostrado provocando el
   fallo**, no razonado.
4. Upload y delete quedan en `audit_log`, con su mutación.
5. Los cuatro huecos de arriba están resueltos o declarados con su motivo en
   `apply-progress.md`. Ninguno queda sin mencionar.

## Depende de

`back/2026-09-07-fix-liquibase-rbac-order`.
