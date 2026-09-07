# Tasks — T-FE-11: Dashboard en Tiempo Real

## Fase A: Instalar Chart.js + ng2-charts

- [ ] **A.1** — Instalar (si no está):
  ```bash
  npm install chart.js ng2-charts
  ```

- [ ] **A.2** — Importar ChartsModule en app.config.ts (standalone).

**Compuerta A:** `npm list chart.js ng2-charts` muestra versiones.

## Fase B: DashboardComponent + GET inicial

- [ ] **B.1** — Crear `features/dashboard/home/home.component.ts`.
  - Standalone: true, imports: [ChartsModule]
  - Inyectar: HttpClient, SupabaseClient
  - Properties: pieChartData, barChartData, stats
  - ngOnInit: GET /api/resultados/consolidado, inicializa charts

- [ ] **B.2** — Crear template con:
  - canvas-based-chart (ng2-charts)
  - IonCard con stats (total, aprobadas, etc.)

- [ ] **B.3** — Métodos:
  ```typescript
  ngOnInit() {
    this.http.get<ResultadosDTO>('/api/resultados/consolidado')
      .subscribe(data => {
        this.updateCharts(data);
      });
    this.subscribeToRealtime();
  }
  ```

**Compuerta A:** `ng build`. Charts se dibujan con datos iniciales.

## Fase C: Supabase Realtime

- [ ] **C.1** — Método `subscribeToRealtime()`:
  ```typescript
  private subscribeToRealtime() {
    this.supabase
      .channel('actas_realtime')
      .on('postgres_changes', 
        { event: '*', schema: 'public', table: 'acta' },
        (payload) => this.handleActaChange(payload)
      )
      .subscribe();
  }
  
  private handleActaChange(payload) {
    // payload.new = nueva fila
    // actualizar chart.data con nuevo estado
    this.updateChartData(payload.new);
    this.chart.update();
  }
  ```

- [ ] **C.2** — En handleActaChange: actualizar contadores (estado, cargo) en memoria.

- [ ] **C.3** — Llamar `this.chart.update()` para re-renderizar.

**Compuerta C:** Realtime funciona en dev. Crear acta en otro dispositivo, dashboard actualiza.

## Fase D: Cleanup

- [ ] **D.1** — En ngOnDestroy, desuscribirse de Realtime:
  ```typescript
  ngOnDestroy() {
    this.supabase.removeAllChannels();
  }
  ```

**Compuerta D:** No memory leaks. unsubscribe al desmontar.

## Qué NO hacer

- **No cache sofisticado** — datos en memoria es suficiente
- **No predicciones** — solo agregados actuales
- **No comparativa período** — out of scope
- **No filtros complejos** — simple view

## Compuertas

| Fase | Comando | Expected |
|------|---------|----------|
| A | `ng build` | charts rendered |
| B | GET /api/resultados/consolidado visible en Network | datos cargan |
| C | Registrar acta en otro dispositivo, DevTools | Realtime evento recibido |
| D | Navegar away, DevTools Sources | no memory leaks |

## Verificación por Mutación

- **V.1** — Comentar subscribeToRealtime() → cambios no se reciben → restaurar
- **V.2** — Comentar chart.update() → chart.data actualiza pero UI no refleja → restaurar
