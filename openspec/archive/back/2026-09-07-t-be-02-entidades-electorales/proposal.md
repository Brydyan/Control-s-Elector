# Proposal — T-BE-02 · Entidades electorales

Origen: `documents/task-control/backendtask/COMPLETADO-T-BE-02.md` · Área: back
Fecha: 2026-09-07 · **Change retrospectivo**

---

## Qué cubre

`Cargo`, `Partido`, `Candidato` y `JrvCargo` — el modelo de qué se elige, quién se presenta y
qué cargos aplican a cada mesa.

## Por qué merece un change propio

`JrvCargo` es la pieza de la que depende que un acta sea válida: define qué cargos se votan
en cada JRV según su nivel geográfico. Si dice de más, se registran actas de un cargo que no
se votaba en esa mesa; si dice de menos, un cargo real queda sin registrar y nadie lo nota
hasta el recuento.

`ObserverController.cargosDeJrv` la consulta en cada registro. `ConfiguracionServiceTest`
existe con 7 tests que **sí pasan** — son de los pocos que no necesitan contexto de Spring.

## Estado real

| Pieza | Estado |
|---|---|
| `Cargo` con `nivel` (NACIONAL / PROVINCIA / CANTON / PARROQUIA) | existe |
| `Partido`, `Candidato` | existen |
| `JrvCargo` con clave compuesta + flag `activo` | existe |
| Migración `008-configuracion.sql` | aplicada |
| Tests de la coherencia nivel ↔ geografía | **ninguno** |

## El hueco: nada comprueba que `JRV_CARGO` sea coherente

`CLAUDE.md` fija la tabla de niveles: Prefecto es PROVINCIA y aplica a todas las JRVs;
Vocal de Junta Parroquial Rural es PARROQUIA y aplica **sólo en parroquias rurales**.

El seed de `infra/sql/03-seed.sql` hace un **cross join**: «JRV_CARGO: todos los cargos en
todas las JRVs». Eso asigna vocales de junta parroquial rural a JRVs urbanas, que es
electoralmente imposible.

En dev da igual. En producción, un OBSERVER de una mesa urbana vería un cargo que no existe
en su papeleta, y el dashboard esperaría un acta que nunca va a llegar.

## Alcance

- Verificar por mutación las garantías de este modelo.
- Comprobar si la coherencia nivel ↔ geografía se valida en algún sitio, y si no, decidir
  dónde va.
- Aclarar si el cross join del seed es sólo de dev.

## Fuera de alcance

- **No** se reescriben las entidades.
- **No** se tocan los endpoints de configuración: eso es T-BE-10.

## Criterio de cierre

1. Cada garantía del spec tiene su mutación ejecutada, con el nombre del test que cayó.
2. La coherencia nivel ↔ geografía está validada, o declarada explícitamente como
   responsabilidad del MASTER al configurar. No a medias.

## Depende de

`back/2026-09-07-fix-liquibase-rbac-order`.
