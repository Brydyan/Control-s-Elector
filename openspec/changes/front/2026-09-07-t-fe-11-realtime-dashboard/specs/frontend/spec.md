# Spec — T-FE-11: Dashboard en Tiempo Real

## Escenarios

### S1 — Cargar estado inicial

```gherkin
GIVEN    usuario navega a /dashboard
WHEN     DashboardComponent carga
THEN     GET /api/resultados/consolidado
AND      datos retornan {actas_total, actas_aprobadas, por_estado, por_cargo}
AND      gráficos se dibujan
```

### S2 — Realtime: nueva acta registrada

```gherkin
GIVEN    gráficos cargados, usuario viendo dashboard
AND      observer en otro dispositivo registra nueva acta
WHEN     Backend INSERT en tabla acta
THEN     Supabase Realtime emite evento
AND      DashboardComponent recibe cambio
AND      chart.data se actualiza en memoria
AND      chart.update() re-renderiza
AND      usuario ve cambio al instante (sin recargar)
```

### S3 — Realtime: acta cambia estado

```gherkin
GIVEN    acta en estado ENVIADA (pie chart muestra 1 en ENVIADA)
WHEN     coordinator cambia a EN_DISPUTA
THEN     Realtime emite UPDATE
AND      chart.data actualiza: -1 ENVIADA, +1 EN_DISPUTA
AND      pie chart re-renderiza
```

### S4 — Gráficos específicos

```gherkin
GIVEN    dashboard cargado
THEN     Pie: estado (ENVIADA, EN_DISPUTA, CORREGIDA, APROBADA)
AND      Bar: votos por cargo (Prefecto, Alcalde, etc.)
AND      Stats: total sufragantes, actas aprobadas, en disputa
```

## Mutaciones

### M1 — No suscribirse a Realtime

Si se comenta el Realtime.on('INSERT'), cambios en `acta` no se reciben. Verificación: registrar acta en otro dispositivo, dashboard no actualiza (detectar falla).

### M2 — No actualizar chart

Si se comenta `this.chart.update()` tras Realtime evento, chart.data cambia pero UI no se ve. Verificación: cambio de estado, chart.data tiene nuevo valor pero UI muestra lo viejo (detectar falla).

## Garantías

- **G1:** Estado inicial GET /api/resultados/consolidado
- **G2:** Realtime suscripción a cambios
- **G3:** Chart se actualiza en tiempo real
- **G4:** Sin recargar página
