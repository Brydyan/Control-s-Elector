# Proposal — T-BE-07 · Ciclo de vida del acta

Origen: `documents/task-control/backendtask/COMPLETADO-T-BE-07.md` · Área: back
Fecha: 2026-09-07 · **Change retrospectivo**

---

## Qué cubre

Los cuatro métodos de `ActaService` que mueven el acta después de enviada:
`marcarEnDisputa`, `corregir`, `aprobar`, `anular`. Más `NotificacionService` y
`ActaEstadoInvalidoException`.

## La máquina de estados, tal como está implementada

```
                   ┌──────────────────────────────┐
                   ▼                              │
ENVIADA ──disputar──► EN_DISPUTA ──corregir──► CORREGIDA
   │                                              │
   └──────────────aprobar─────────┬───────────────┘
                                  ▼
                              APROBADA ──anular (MASTER)──► ANULADA
```

`BORRADOR` está en el enum y ningún camino lo produce.
`ANULADA` es terminal: ningún método la acepta como estado de partida.

## Estado real

Los cuatro métodos existen, con sus guards de estado, su auditoría y sus notificaciones.
**Ningún test los cubre**: los 22 de `CoordinatorControllerTest` y `ObserverControllerTest`
están en ERROR.

## Las tres asimetrías que este change tiene que resolver

**1. `aprobar` no notifica.** `marcarEnDisputa` y `anular` llaman a `notificacionService`;
`aprobar` y `corregir` no. `backend.md:117` promete «Notificación automática al cambiar
estado». Puede ser deliberado —sólo se avisa lo que exige acción del OBSERVER— pero nadie lo
ha decidido por escrito.

**2. `corregir` no audita quién es quién.** El detalle que registra es
`"Acta corregida por usuario " + usuarioId`, sin distinguir si fue el OBSERVER dueño o un
COORDINATOR corrigiendo directamente. El parámetro `esCoordinator` existe y no llega al
`audit_log`. Cuando alguien impugne una corrección, esa distinción es exactamente la que hará
falta.

**3. `corregir` valida el invariante de votos; `registrar` también.** Dos copias de la misma
regla, en el mismo archivo. Es §4.2: hay que comprobar que las dos están cubiertas por tests,
no sólo una.

## Alcance

- Escribir las garantías de la máquina de estados y verificarlas por mutación.
- Resolver la asimetría de notificaciones.
- Cerrar el hueco de auditoría de `corregir`.

## Fuera de alcance

- **No** se toca `registrar`: es T-BE-06.
- **No** se implementa el envío real de notificaciones (Supabase Realtime): es T-IN-06.
- **No** se tocan los endpoints: son T-BE-11 y T-BE-12.

## Criterio de cierre

1. Cada transición válida y cada transición prohibida tienen su test.
2. Cada guard de estado tiene su mutación ejecutada, con el nombre del test que cayó.
3. La asimetría de notificaciones está decidida por escrito.
4. El `audit_log` de `corregir` distingue OBSERVER de COORDINATOR.

## Depende de

`back/2026-09-07-fix-liquibase-rbac-order`. El test de ciclo completo lo aporta
`back/2026-09-07-t-be-15-tests-integracion` (RF-TEST-03).
