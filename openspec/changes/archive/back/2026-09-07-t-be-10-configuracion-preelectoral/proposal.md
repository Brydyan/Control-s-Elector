# Proposal — T-BE-10 · Configuración preelectoral (MASTER)

Origen: `documents/task-control/backendtask/COMPLETADO-T-BE-10.md` · Área: back
Fecha: 2026-09-07 · **Change retrospectivo**

---

## Qué cubre

Los endpoints con los que MASTER prepara la elección antes de que empiece: CRUD de cargos,
partidos y candidatos.

## Por qué importa el orden de las cosas

Esta configuración es **prerrequisito de todo lo demás**. Sin cargos no hay `JRV_CARGO`; sin
`JRV_CARGO` el OBSERVER no ve qué registrar; sin candidatos no hay `ActaDetalle`. Y se hace
una vez, contra reloj, en los días previos.

Un error aquí no se descubre hasta que 450 mesas intentan registrar y no pueden.

## Estado real

| | |
|---|---|
| `CargoController`, `PartidoController`, `CandidatoController` | existen |
| `ConfiguracionService` | existe |
| `ConfiguracionServiceTest` | **7 tests, en verde** — de las pocas suites que ya pasaban |
| `CargoControllerTest`, `PartidoControllerTest`, `CandidatoControllerTest` | 10 + 9 + 10 tests, **los 29 en ERROR** |
| Migración `008-configuracion.sql` | aplicada |

Es de las tareas mejor cubiertas del proyecto — en cuanto la suite arranque, hay 36 tests
esperando.

## Los tres huecos

**1. Falta `POST /jrvs/{jrvId}/cargos`.** `backend.md:164` lo promete para asignar cargos a
JRVs (el mapeo `JRV_CARGO`). Es **la pieza central** de la configuración: sin ella, la
relación entre mesas y cargos sólo puede crearse por SQL directo, que es lo que hace el seed
con un cross join.

**2. Nada valida la coherencia nivel ↔ geografía.** Un cargo de nivel PARROQUIA (Vocal de
Junta Parroquial Rural) puede asignarse a una JRV urbana. `CLAUDE.md` fija la tabla de
niveles; ningún código la aplica. Ver `t-be-02-entidades-electorales` RF-ELE-03.

**3. `POST /usuarios` y `GET /observers`** los promete `backend.md:166-167`. El alta de
usuarios existe como `POST /api/v1/auth/register` (T-BE-09), no bajo `/usuarios`. El listado
de observers hay que comprobarlo.

## Alcance

- Verificar por mutación las garantías de los tres CRUD.
- Decidir qué pasa con el endpoint de `JRV_CARGO`: implementarlo o retirar la promesa.
- Comprobar si la configuración se audita (probablemente no).

## Fuera de alcance

- **No** se toca el alta de usuarios: es T-BE-09.
- **No** se implementa la validación nivel ↔ geografía sin haber decidido dónde vive esa
  regla (`t-be-02-entidades-electorales` B.1).

## Criterio de cierre

1. Las 36 pruebas existentes pasan.
2. Cada garantía del spec tiene su mutación ejecutada.
3. El endpoint de `JRV_CARGO` existe con su test, o `backend.md:164` deja de prometerlo.
4. La auditoría de la configuración existe, o su ausencia está declarada.

## Depende de

`back/2026-09-07-fix-liquibase-rbac-order`.
