# Proposal — T-BE-06 · Registro de acta con locking pesimista

Origen: `documents/task-control/backendtask/COMPLETADO-T-BE-06.md` · Área: back
Fecha: 2026-09-07 · **Change retrospectivo**

---

## Qué cubre

`ActaService.registrar`: la operación más crítica del sistema. Concentra dos garantías de las
que depende que el conteo sea válido:

1. **Una acta por `jrv_id + cargo_id`** — vía `findActaActivaConLock` (`SELECT ... FOR UPDATE`).
2. **`SUM(detalles.votos) + votosBlancos + votosNulos = totalSufragantes`**.

## Por qué es el change retrospectivo más importante

Si la primera falla, dos veedores registran resultados distintos para la misma mesa y el
sistema no sabe cuál es el bueno. Si la segunda falla, entra un acta que no cuadra y el
resultado consolidado es falso.

**Ninguna de las dos tiene un test que la pruebe hoy.**

## Estado real

| | |
|---|---|
| Método implementado | sí |
| `findActaActivaConLock` con `@Lock(PESSIMISTIC_WRITE)` | sí |
| Validación de la suma, con mensaje detallado | sí |
| Auditoría `REGISTRO_ACTA` | sí |
| Test de concurrencia | **no** |
| Test de suma inválida | **no** |

## Decisión arquitectónica que este change registra

`ActaService.registrar` **ya no comprueba la pre-asignación de JRV**. Las líneas 77-82 lo
documentan:

> Verificacion de ObserverJrvAsignacion removida. Decision arquitectonica (Engram 2026-07-11):
> OBSERVER selecciona JRV libremente sin pre-asignacion. RLS PostgreSQL + permisos JWT
> ('acta:upload') son el control de acceso vigente.

Es una decisión tomada y correcta de registrar. **Pero apoya el control de acceso en RLS, y
RLS no está verificada**: `RlsInterceptor` no tiene ningún test, hace `SET LOCAL` desde un
filtro de servlet —fuera de la transacción del repositorio— y se traga cualquier excepción.

Un comentario que afirma que una defensa está vigente, sobre una defensa que nadie ha
comprobado que actúe. Es §4.3 del playbook, y aquí protege el registro de resultados.

## Hallazgo adicional

`registrar` resuelve cada candidato por id y **no comprueba que pertenezca al cargo del
acta**. Se puede registrar un acta de Alcalde con votos de un candidato a Prefecto: la suma
cuadra, la validación pasa, y el dashboard atribuye los votos al cargo equivocado sin ningún
error. Ver `t-be-02-entidades-electorales`, RF-ELE-05.

## Alcance

- Escribir las garantías y verificarlas por mutación.
- Confirmar o desmentir que RLS actúa, dado que la arquitectura descansa en ella.
- Registrar el hueco de candidato/cargo con su reproducción.

## Fuera de alcance

- **No** se implementan los tests de concurrencia: los aporta `t-be-15-tests-integracion`.
- **No** se toca el ciclo de vida posterior: es T-BE-07.
- **No** se arregla RLS aquí. Se mide y se reporta.

## Criterio de cierre

1. Las dos garantías tienen test **y** mutación ejecutada.
2. La pregunta «¿RLS está actuando?» tiene respuesta medida, no supuesta.
3. El hueco candidato/cargo está reproducido y documentado.

## Depende de

`back/2026-09-07-fix-liquibase-rbac-order` y `back/2026-09-07-t-be-15-tests-integracion`.
