# Guía de Recintos Electorales

## Introducción

Un **Recinto Electoral** es una agrupación geográfica de una o más Juntas Receptoras de Votos (JRVs) dentro de una parroquia. Cada Recinto tiene asignado un **Coordinador** que es responsable de revisar y aprobar las Actas de votación enviadas por los Observadores de cada JRV.

## Componentes

### 1. Recinto
- **Nombre**: Identificación del recinto (ej: "Escuela República del Ecuador")
- **Parroquia**: Ubicación geográfica (selección desde cascada: Provincia → Cantón → Parroquia)
- **Coordinador**: Usuario con rol `COORDINATOR` asignado a este recinto
- **Estado**: `ACTIVO` o `INACTIVO`
- **Mesas (JRVs)**: Cantidad de JRVs asignadas al recinto

### 2. Coordinador
- Rol: `COORDINATOR`
- Responsabilidades:
  - Revisar Actas pendientes enviadas por Observadores
  - Aprobar Actas válidas
  - Rechazar Actas con datos incompletos o inconsistencias
  - Recibir notificaciones en tiempo real de nuevas Actas

## Flujo de Trabajo

### Admin: Crear un Recinto

1. Ir a **Configuración** → **Recintos**
2. Hacer click en **[Crear Recinto]**
3. Completar el formulario:
   - Seleccionar **Provincia** (cascada)
   - Seleccionar **Cantón**
   - Seleccionar **Parroquia**
   - Ingresar **Nombre del Recinto**
   - Asignar **Coordinador** (opcional en creación)
   - Seleccionar **Estado** (por defecto: ACTIVO)
4. Click en **[Guardar]**

### Admin: Asignar JRVs a un Recinto

1. Ir a **Configuración** → **Recintos**
2. En la tabla, hacer click en **[Asignar JRVs]** para el recinto deseado
3. Seleccionar las JRVs de la parroquia que pertenecen a este recinto
4. Click en **[Guardar]**

> Las JRVs deben pertenecer a la misma parroquia del Recinto

### Coordinator: Revisar Actas Pendientes

1. Ir a **Actas Pendientes**
2. Ver lista de actas en estado **ENVIADA** (tab: Pendientes)
3. Para cada acta:
   - Click en **[Ver Detalle]** para revisar:
     - Foto del acta
     - Número de sufragantes
     - Distribución de votos (blancos, nulos)
     - Votos por candidato
   - **Aprobar**: Si los datos son correctos
   - **Rechazar**: Si hay inconsistencias (el Observador recibirá notificación)

### Coordinator: Recibir Notificaciones

1. Bell icon en la barra superior
2. Ver badge con contador de notificaciones sin leer
3. Tipos de notificaciones:
   - **ACTA_ENVIADA**: Nueva acta pendiente de revisión
   - **ACTA_APROBADA**: Confirmación de aprobación (referencial)
   - **ACTA_RECHAZADA**: Confirmación de rechazo

## Estados de Acta

```
BORRADOR
   ↓
ENVIADA ← Observador sube acta
   ↓
(Coordinator revisa)
   ├→ APROBADA (aceptada)
   │
   └→ EN_DISPUTA (rechazada con motivo)
        ↓
     CORREGIDA ← Observador corrige y reenvía
```

## RLS (Row-Level Security)

- **Admin (MASTER)**: Ve todos los Recintos de todas las parroquias
- **Coordinator**: Ve SOLO los Recintos asignados a su usuario
- **Observer**: Ve SOLO sus propias Actas

## Preguntas Frecuentes

### ¿Puedo eliminar un Recinto?
No, si tiene JRVs asignadas. Primero debe reasignar todas las JRVs a otro Recinto o eliminarlas.

### ¿Qué pasa si cambio el Coordinador?
El Coordinador anterior ya no recibirá notificaciones de Actas de este Recinto. Las Actas que ya aprobó siguen siendo válidas.

### ¿Cuántos Coordinadores por Recinto?
Uno (1). Un Coordinador puede estar asignado a múltiples Recintos de diferentes parroquias.

### ¿Las notificaciones son en tiempo real?
En la versión MVP, se usan polls cada 5-10 segundos. Phase 2: WebSocket para actualización instantánea.

## API REST

| Endpoint | Método | Descripción |
|----------|--------|-------------|
| `/admin/recintos` | GET | Listar todos los Recintos (MASTER only) |
| `/admin/recintos` | POST | Crear Recinto (MASTER only) |
| `/admin/recintos/{id}` | PUT | Actualizar Recinto (MASTER only) |
| `/admin/recintos/{id}` | DELETE | Eliminar Recinto (MASTER only) |
| `/coordinator/actas-pendientes` | GET | Listar Actas del Coordinator |
| `/coordinator/actas/{id}/aprobar` | PUT | Aprobar Acta |
| `/coordinator/actas/{id}/rechazar` | PUT | Rechazar Acta |
| `/notifications/pendientes` | GET | Obtener Notificaciones sin leer |
| `/notifications/{id}/leer` | PUT | Marcar como leída |

## Troubleshooting

| Problema | Causa | Solución |
|----------|-------|----------|
| Coordinator no ve Actas | No tiene Recintos asignados | Admin debe asignar Recintos al Coordinator |
| Acta no aparece en "Revisadas" | Aún está en estado ENVIADA | Realizar acción (Aprobar/Rechazar) |
| No recibe notificaciones | Polling está deshabilitado | Verificar navegador permite polling (D3 MVP) |
| Error 409 al eliminar Recinto | Recinto tiene JRVs asignadas | Reasignar o eliminar JRVs primero |
