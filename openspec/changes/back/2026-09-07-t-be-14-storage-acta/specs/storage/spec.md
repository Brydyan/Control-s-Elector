# Spec — Storage de imágenes (delta)

Dominio: `storage`. Change: `2026-09-07-t-be-14-storage-acta`.

---

## RF-STO-01 — El OBSERVER sube la imagen de su acta

GIVEN un OBSERVER autenticado con permiso `acta:upload` y un acta que él registró
WHEN hace `POST /api/v1/observer/actas/{actaId}/imagen` con un multipart `imagen`
THEN la respuesta es `201 Created`
AND el objeto existe en el bucket
AND el acta queda con su referencia de storage persistida

Hoy este endpoint devuelve `501 NOT_IMPLEMENTED` (`ObserverController:191-196`).

**Mutación**: quitar la persistencia de la referencia en el acta, dejando sólo el upload.
Debe caer el test que lee el acta después de subir.

---

## RF-STO-02 — Validación del archivo antes de tocar S3

GIVEN un archivo vacío, o mayor de 10 MB, o con `Content-Type` que no empieza por `image/`
WHEN se intenta subir
THEN la respuesta es `400 Bad Request`
AND **no** se creó ningún objeto en el bucket

El límite de 10 MB y el prefijo `image/` son los que ya aplica `EvidenciaService.validate`.
La ruta de acta usa los mismos: dos rutas con límites distintos es el patrón §4.2 —una regla
aplicada en un sitio y no en su vecino.

**Mutación**: subir el límite a 100 MB en una de las dos rutas. Debe caer el test de tamaño
de **esa** ruta. Si sólo existe el test de una, la otra no está protegida.

---

## RF-STO-03 — Un fallo de base de datos no deja objetos huérfanos

GIVEN una subida donde el INSERT falla en el **commit** (no en el `save()`)
WHEN termina la petición
THEN el objeto **no** existe en el bucket

Éste es el requisito que hoy no se cumple pese a que el código afirma cumplirlo.
`EvidenciaService.upload` es `@Transactional` y su `catch` rodea `repository.save(...)`, que
con JPA sólo encola el INSERT: el fallo de constraint ocurre al cerrar la transacción, fuera
del `catch`. Ver `design.md` D2.

**Escenario concreto**: provocar una violación de constraint en el commit y comprobar con
`s3.exists(key)` que el objeto no quedó.

**Mutación**: devolver la compensación al `try/catch` alrededor de `save()`.
Debe caer el test de huérfanos. Si no cae, el test está mockeando S3 y no prueba nada
(`design.md` D3).

---

## RF-STO-04 — Un borrado que falla en S3 no se reporta como éxito

GIVEN una evidencia cuyo objeto en S3 no se puede borrar
WHEN se llama `DELETE /api/v1/evidencias/{id}`
THEN la respuesta **no** es `204 No Content`

Hoy `EvidenciaService.delete` captura la excepción de S3, la registra en el log y devuelve
éxito. El usuario recibe «borrado» mientras la foto sigue accesible por presigned URL.

**Mutación**: reintroducir el `catch` que se traga el error.
Debe caer el test de borrado fallido.

---

## RF-STO-05 — Subida y borrado quedan auditados

GIVEN cualquier subida o borrado de imagen, de acta o de evidencia
THEN queda una fila en `audit_log` con el usuario, la acción, la entidad, su id y la IP

Hoy **ninguna** de las dos operaciones se audita: ni `EvidenciaService.upload` ni `delete`
llaman a `AuditLogService`. Borrar la foto de un acta es destruir la prueba de un resultado;
tiene que dejar rastro.

**Mutación**: quitar la llamada a `auditLogService.registrar` de `delete`.
Debe caer un test que cuente filas de `audit_log`.

---

## RF-STO-06 — El acta guarda la clave, no una URL que caduca

GIVEN un acta con imagen subida
THEN el campo de storage contiene la **clave** del objeto
(`actas/{jrvId}/{cargoId}/{uuid}.webp`), no una URL prefirmada

La presigned URL expira en 15 minutos (`S3StorageService.PRESIGN_EXPIRY`). Persistirla
produce un dato que está caducado casi siempre pero parece válido. Se genera al leer.

Ver `design.md` D1: el nombre `storageUrl` contradice este requisito y hay que renombrarlo o
documentarlo explícitamente en la entidad.

---

## RF-STO-07 — La expiración de la presigned URL es una sola, y está documentada

GIVEN una petición de descarga
THEN la URL prefirmada expira en el plazo declarado en la configuración

Hoy el código usa 15 minutos y `T-BE-14.md:92` dice 24 horas. Una de las dos afirmaciones
sobra. El valor debe salir de configuración, no de una constante, para que prod y dev puedan
diferir sin editar código — `S3StorageService:110-111` ya anticipa querer 5 minutos en prod.

---

## RNF-STO-01 — Los tests de storage corren contra un S3 real de test

GIVEN la suite de storage
THEN usa un contenedor MinIO (Testcontainers), no un doble de `S3StorageService`

Con un mock, `exists(key)` devuelve lo que el test programó: la aserción comprueba el test,
no el sistema. Justo en la frontera —¿el objeto quedó o no quedó?— un doble no prueba nada.
Ver `design.md` D3.
