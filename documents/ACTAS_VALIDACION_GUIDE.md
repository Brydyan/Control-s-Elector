# Guía de Validación de Actas Electorales (Coordinator)

## Rol: Coordinador de Recinto

Como **Coordinador de Recinto**, eres responsable de revisar todas las Actas de votación enviadas por los Observadores de las JRVs dentro de tu Recinto.

## Tu Dashboard

### Acceso
- **Menú Principal** → **Actas Pendientes**
- O directamente desde la URL: `/coordinator/actas-pendientes`

### Tabs Disponibles

#### Tab 1: Pendientes (ENVIADA)
- Muestra todas las Actas que requieren tu revisión
- Polling automático cada 5 segundos para detectar nuevas Actas
- Prioriza por fecha de envío (más reciente arriba)

#### Tab 2: Revisadas
- Histórico de Actas que ya procesaste
- Estados: `APROBADA` o `EN_DISPUTA`
- Consulta para auditoría

## Información de Cada Acta

Cada Acta muestra:
- **JRV**: Número de la Junta Receptora de Votos
- **Cargo**: Dignidad electoral (Presidente, Concejal, etc.)
- **Observer**: Nombre del Observador que envió la Acta
- **Estado**: Actual (ENVIADA, APROBADA, EN_DISPUTA, CORREGIDA)
- **Fecha**: Cuándo se envió
- **Mesas**: Cantidad de mesas/electores

## Proceso de Validación

### Paso 1: Ver Detalle del Acta

1. Click en **[Ver Detalle]** en la tarjeta del Acta
2. Se abre modal con:
   - **Foto del Acta**: Captura de pantalla del acta original
   - **Resumen de Votos**:
     - Sufragantes totales
     - Votos blancos
     - Votos nulos
   - **Tabla de Candidatos**: Nombre y votos por candidato

### Paso 2: Revisar Consistencias

Verifica los siguientes puntos:

#### Sumatoria de Votos
```
Votos esperados = Sufragantes totales
Votos reales = Votos_Candidatos + Blancos + Nulos

¿Coinciden?
- SÍ → Continuar
- NO → RECHAZAR (ver Paso 3)
```

#### Coherencia de Datos
- ¿Los números de candidatos están en orden?
- ¿Faltan candidatos en la lista?
- ¿Los votos de cada candidato son razonables?
- ¿La foto del acta es legible?

#### Validación por Cargo
- Algunos cargos tienen candidatos únicos (Presidente)
- Otros tienen múltiples candidatos (Concejales)
- Verifica que cada candidato pertenezca al cargo correcto

### Paso 3: Acción - Aprobar

**Criterio**: Los datos son correctos, suma de votos coincide, foto legible.

1. Click en **[Aprobar]** (botón verde)
2. Confirmación inmediata
3. Acta pasa a estado `APROBADA`
4. Observer recibe notificación automática
5. Acta aparece en tab **Revisadas**

**Efecto**: El Acta se contabiliza como válida en reportes finales.

### Paso 4: Acción - Rechazar

**Criterio**: Datos inconsistentes, sumatoria incorrecta, foto ilegible, o discrepancias.

1. Click en **[Rechazar]** (botón rojo)
2. Se abre modal con TextArea
3. **Escribe motivo**:
   - Sé específico: "Suma de votos no coincide: esperados 450, reales 448"
   - Máximo 500 caracteres
   - Da instrucciones claras para corregir
4. Click en **[Rechazar]** (confirmar)
5. Acta pasa a estado `EN_DISPUTA`
6. Observer recibe notificación con tu motivo
7. Observer debe corregir y reenviar

**Efecto**: El Acta queda en disputa. Observer la corrige y reenvía como `CORREGIDA`.

## Estados de Acta Explicados

| Estado | Significado | Acciones Disponibles |
|--------|-----------|----------------------|
| ENVIADA | Acta pendiente de tu revisión | Aprobar, Rechazar |
| APROBADA | Ya revisada y válida | Ver histórico |
| EN_DISPUTA | Rechazada, en espera de corrección | Ver motivo de rechazo |
| CORREGIDA | Observer corrigió y reenvió | Revisar nuevamente |

## Notificaciones en Tiempo Real

### Bell Icon (Barra Superior)

- **Rojo + Número**: Notificaciones sin leer
- **Gris**: Sin notificaciones nuevas

### Tipos de Notificaciones

1. **ACTA_ENVIADA**: Nueva Acta pendiente de revisión
   - Acción: Click para abrir detalle
2. **ACTA_APROBADA**: Confirmación de tu aprobación
   - Referencial, ya procesada
3. **ACTA_RECHAZADA**: Confirmación de tu rechazo
   - Referencial, contiene el motivo

### Polling
- Cada 5 segundos se chequean nuevas Actas (Pendientes)
- Cada 10 segundos se chequean nuevas Notificaciones
- MVP: Polling. Phase 2: WebSocket para actualización instantánea

## Recomendaciones

### ✅ Buenas Prácticas

1. **Revisa en orden**: Comienza por las Actas más antiguas
2. **Sé rápido**: Los Observadores esperan retroalimentación
3. **Motivos claros**: Al rechazar, sé específico en el motivo
4. **Verifica fotos**: Asegúrate que la foto sea legible
5. **Valida sumatoria**: Siempre chequea que suma de votos = sufragantes
6. **Consulta patrones**: Si ves patrón inusual, investiga antes de aprobar

### ❌ Evita

1. **No hagas suposiciones**: Si algo no cierra, rechaza
2. **No apruebes datos inconsistentes**: Mantén integridad electoral
3. **No olvides revisar candidatos**: Verifica orden y completitud
4. **No ignores notificaciones**: Responde rápido a correcciones

## Preguntas Frecuentes

### ¿Qué pasa si rechazó un Acta pero fue error mío?
- No hay "deshacer". El Observer debe reenviar (como CORREGIDA).
- Si fue error de verdad, coordina con el Observer para reenvío manual.

### ¿Puedo ver todas las Actas de todos los Recintos?
- NO. Solo ves Actas de tus Recintos asignados (RLS).
- Si necesitas Actas de otro Recinto, coordina con otro Coordinator.

### ¿Cuánto tiempo tengo para revisar un Acta?
- No hay límite de tiempo en el sistema.
- Recomendación: Revisar dentro de 1-2 horas del envío.

### ¿Qué pasa si la foto está borrosa?
- RECHAZA con motivo: "Foto ilegible, por favor reenvía con mejor calidad"
- Observer reenviará con nueva foto

### ¿Debo aprobar Actas de JRVs que no conozco?
- Sí. Revisa aplicando los mismos criterios de sumatoria e integridad.
- Tu rol es validación de datos, no conocimiento local.

## Métricas Esperadas

### KPIs para Coordinators

- **Tiempo promedio de revisión**: < 5 minutos
- **Tasa de aprobación**: 85-95% (5-15% rechazos son normales)
- **Tasa de corrección exitosa**: > 90% (Actas rechazadas que se corrigen)

## Support

Si encuentras problemas:
1. Revisa esta guía
2. Contacta al Admin del sistema
3. Escala a Soporte Electoral

---

**Versión**: MVP (Phase 1)  
**Última actualización**: 2026-11-19  
**Próximas mejoras**: WebSocket notifications, Bulk approve/reject (Phase 2)
