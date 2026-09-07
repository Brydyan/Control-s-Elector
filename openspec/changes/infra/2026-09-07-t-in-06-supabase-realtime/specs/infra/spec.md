# Spec — T-IN-06: Supabase Realtime

## Escenarios

### S1 — Realtime habilitado

```gherkin
GIVEN    proyecto Supabase con BD ready
WHEN     habilitar Realtime en Settings
THEN     Realtime aparece como "Enabled" en dashboard
```

### S2 — Cliente suscribirse a cambios

```gherkin
GIVEN    DashboardComponent cargado
AND      Realtime habilitado
WHEN     `supabase.channel('actas_realtime').on('postgres_changes', ...)`
THEN     WebSocket se abre
AND      cliente listo para recibir eventos
```

### S3 — Evento INSERT

```gherkin
GIVEN    cliente suscrito a tabla "acta"
WHEN     observer registra acta (INSERT en "acta")
THEN     Realtime emite evento {type: 'INSERT', new: {id, estado: 'ENVIADA', ...}}
AND      cliente recibe en callback
AND      DashboardComponent.handleActaChange() ejecuta
AND      chart se actualiza
```

### S4 — Evento UPDATE

```gherkin
GIVEN    acta existente en "acta"
WHEN     coordinator cambia estado (UPDATE)
THEN     Realtime emite evento {type: 'UPDATE', new: {id, estado: 'APROBADA'}, old: {...}}
AND      chart actualiza (menos 1 ENVIADA, más 1 APROBADA)
```

## Mutaciones

### M1 — No suscribirse

Si se comenta .on() en cliente, no recibe eventos. Verificación: INSERT/UPDATE en BD, chart no actualiza.

### M2 — Realtime deshabilitado en Supabase

Si se deshabilita en Settings, .on() nunca recibe eventos. Verificación: INSERT, nada pasa.

## Garantías

- **G1:** Realtime habilitado
- **G2:** Cliente puede suscribirse
- **G3:** INSERT/UPDATE disparan eventos
- **G4:** Client recibe en tiempo real
