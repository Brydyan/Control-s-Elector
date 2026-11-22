# Diagrama 03 — Flujos de Datos (Índice)

Elecciones Seccionales 2027-2031 — Provincia de Santa Elena.
Roles: `MASTER` · `COORDINATOR` · `ADMIN` · `OBSERVER`

```
diagram/
  secuencias/    → diagramas de secuencia (interacciones entre actores y sistemas)
  flujos/        → diagramas de flujo, estados, casos de uso y referencia
```

---

## Diagramas de Secuencia (`secuencias/`)

### Configuración Pre-Electoral

| # | Archivo | Escenario |
|---|---------|-----------|
| 13 | [13-configuracion-preelectoral.md](secuencias/13-configuracion-preelectoral.md) | MASTER registra cargos, partidos, candidatos, JRVs y usuarios |

### Flujos del Observador

| # | Archivo | Escenario |
|---|---------|-----------|
| 01 | [01-registro-exitoso.md](secuencias/01-registro-exitoso.md) | Happy path: JRV + cargo + candidatos → acta registrada |
| 02 | [02-offline-sync.md](secuencias/02-offline-sync.md) | Sin red: guardado local → sincronización al reconectar |
| 08 | [08-compresion-imagen.md](secuencias/08-compresion-imagen.md) | Pipeline WebP: captura → resize → encode → validar tamaño |

### Autenticación y Sesión

| # | Archivo | Escenario |
|---|---------|-----------|
| 03 | [03-autenticacion-login.md](secuencias/03-autenticacion-login.md) | Login con cédula/contraseña → JWT |
| 04 | [04-token-expirado.md](secuencias/04-token-expirado.md) | JWT vencido → refresh token · MASTER revoca sesión |

### Error y Rechazo

| # | Archivo | Escenario |
|---|---------|-----------|
| 05 | [05-validacion-fallida.md](secuencias/05-validacion-fallida.md) | Totales incorrectos → 422 → corrección → escalación a coordinator |
| 07 | [07-rls-acceso-denegado.md](secuencias/07-rls-acceso-denegado.md) | ⏳ PLANEADO: Intento acceso sin autorización → RLS rechaza |
| 10 | [10-error-storage.md](secuencias/10-error-storage.md) | Falla upload → IndexedDB + private storage → reintento o exportación manual |

### Gestión de Actas

| # | Archivo | Escenario |
|---|---------|-----------|
| 11 | [11-disputa-acta.md](secuencias/11-disputa-acta.md) | Foto no coincide → EN_DISPUTA → corrección → aprobación |
| 12 | [12-anulacion-acta.md](secuencias/12-anulacion-acta.md) | Fraude → MASTER anula acta APROBADA → estado final |

### Integridad y Concurrencia

| # | Archivo | Escenario |
|---|---------|-----------|
| 06 | [06-concurrencia-misma-jrv.md](secuencias/06-concurrencia-misma-jrv.md) | Dos dispositivos misma JRV/cargo → locking pesimista → 409 |

### Dashboard y Notificaciones

| # | Archivo | Escenario |
|---|---------|-----------|
| 09 | [09-dashboard-realtime.md](secuencias/09-dashboard-realtime.md) | WebSocket (mecanismo por definir) → update reactivo por cargo y cantón |
| 14 | [14-notificaciones.md](secuencias/14-notificaciones.md) | Ciclo push: EN_DISPUTA → OBSERVER · corrección → COORDINATOR · anulación → COORDINATOR |

---

## Diagramas de Flujo y Referencia (`flujos/`)

| # | Archivo | Tipo | Descripción |
|---|---------|------|-------------|
| 07b | [07-estado-acta.md](flujos/07-estado-acta.md) | `stateDiagram` | Ciclo de vida completo del acta |
| 08b | [08-caso-de-uso.md](flujos/08-caso-de-uso.md) | `graph` | Casos de uso por actor — presentación a stakeholders |
| 15 | [15-api-endpoints.md](flujos/15-api-endpoints.md) | Referencia | Tabla consolidada de endpoints REST |

---

## Estados del Acta

```
BORRADOR → ENVIADA → EN_DISPUTA → CORREGIDA → APROBADA → ANULADA
```

## Resumen de Roles

| Rol | Responsabilidad principal |
|-----|--------------------------|
| `MASTER` | Registra cargos, partidos, candidatos · gestión global · anula actas |
| `COORDINATOR` | Gestiona disputas, correcciones y aprobaciones (todas las JRVs) |
| `ADMIN` | Solo lectura — dashboard y estadísticas en tiempo real |
| `OBSERVER` | Registra actas (selecciona JRV + Cantón) · corrige si COORDINATOR lo solicita |
