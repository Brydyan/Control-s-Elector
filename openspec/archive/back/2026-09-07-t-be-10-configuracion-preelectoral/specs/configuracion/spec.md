# Spec — Configuración preelectoral (retrospectivo)

Dominio: `configuracion`. Change: `2026-09-07-t-be-10-configuracion-preelectoral`.

---

## RF-CFG-01 — Sólo MASTER escribe la configuración

GIVEN un usuario COORDINATOR, ADMIN u OBSERVER
WHEN llama a un endpoint de escritura de cargos, partidos o candidatos
THEN recibe `403 Forbidden`

GIVEN un MASTER
THEN la operación se acepta

`CLAUDE.md`: «`MASTER` — registra cargos/partidos/candidatos». Y `ADMIN` nunca escribe, en
ningún módulo.

**Mutación**: quitar el `@PreAuthorize("hasRole('MASTER')")` de un endpoint de escritura.
Debe caer el test del rol correspondiente → 403.

Repetir en los tres controladores: son tres copias de la misma regla y basta con que una
quede sin cubrir (§4.2).

---

## RF-CFG-02 — La lectura de la configuración es más amplia que la escritura

GIVEN un OBSERVER
WHEN consulta los candidatos de un cargo
THEN los obtiene: los necesita para registrar el acta

La asimetría es deliberada: leer la papeleta lo necesita todo el mundo; cambiarla, sólo
MASTER.

**Verificar endpoint por endpoint** cuáles son de lectura y cuáles de escritura, y que cada
uno lleva el guard que le toca. Un `@PreAuthorize` de clase que cubra ambos deja a los
OBSERVER sin poder registrar, o a cualquiera pudiendo editar la papeleta.

---

## RF-CFG-03 — Un cargo tiene nivel, y el nivel significa algo

GIVEN un cargo
THEN su `nivel` es NACIONAL, PROVINCIA, CANTON o PARROQUIA

GIVEN la tabla de `CLAUDE.md`
THEN un cargo de nivel PARROQUIA sólo aplica a JRVs en parroquias rurales

**Hoy nada lo valida.** Ver `t-be-02-entidades-electorales` RF-ELE-03: el seed asigna todos
los cargos a todas las JRVs con un cross join, lo que incluye vocales de junta parroquial
rural en mesas urbanas.

Si la validación no vive aquí —en el endpoint que usa el MASTER para configurar— no vive en
ninguna parte, porque éste es el único punto por el que debería entrar esa relación.

---

## RF-CFG-04 — Un candidato pertenece a un cargo y a un partido existentes

GIVEN una petición de alta de candidato con un `cargoId` o `partidoId` inexistente
THEN se rechaza con un error de cliente, no con una violación de FK cruda

**Mutación**: quitar la comprobación de existencia del cargo.
Debe caer el test de candidato con cargo inexistente.

---

## RF-CFG-05 — Los DTOs exponen `publicId`

GIVEN cualquier respuesta de configuración
THEN los identificadores son `publicId`, no los `Long` internos

**Mutación**: cambiar un mapper para exponer el `id` interno.
Debe caer un test de forma del DTO.

---

## RF-CFG-06 — Existe un camino para asignar cargos a una JRV

GIVEN un MASTER
WHEN asigna un cargo a una JRV
THEN queda una fila en `JRV_CARGO` con su flag `activo`

**Este endpoint no existe.** `backend.md:164` lo promete como
`POST /api/jrvs/{jrvId}/cargos`.

Sin él, la relación entre mesas y cargos sólo puede crearse por SQL directo — que es lo que
hace hoy el seed. Es la pieza central de la configuración preelectoral: `JRV_CARGO` decide
qué ve el OBSERVER cuando abre una mesa y qué cuenta el dashboard como pendiente.

**Consecuencia si no se implementa**: cualquier corrección de la configuración el día antes
de la elección —una parroquia mal clasificada, un cargo que faltaba— exige entrar a la base
de datos a mano, sin auditoría y sin validación.

---

## RF-CFG-07 — La configuración se audita

GIVEN cualquier alta, modificación o baja de cargo, partido, candidato o `JRV_CARGO`
THEN queda una fila en `audit_log`

**A verificar; probablemente no ocurre.** `ConfiguracionService` no aparece entre los
llamantes de `AuditLogService` en la lectura del 2026-09-07.

Cambiar la papeleta —añadir un candidato, desactivar un cargo— en mitad del proceso es
exactamente la clase de acción que después alguien va a querer reconstruir. Y a diferencia de
las actas, aquí no hay un histórico del que deducirla.

---

## RF-CFG-08 — Desactivar no es borrar

GIVEN un cargo o un `JRV_CARGO` que ya tiene actas registradas
WHEN se intenta eliminar
THEN o se impide, o se desactiva en vez de borrarse

`JrvCargo` tiene un flag `activo`, lo que sugiere borrado lógico. Hay que confirmar que los
tres CRUD lo respetan y que ninguno permite un `DELETE` físico que deje actas apuntando a un
cargo inexistente.

Es el mismo principio que gobierna las actas: los datos no se borran, cambian de estado.

**Mutación**: si existe la protección, quitarla — debe caer su test.
Si no existe, es un hallazgo: anotarlo con la reproducción (crear cargo → registrar acta →
borrar cargo → ver qué pasa con el acta).
