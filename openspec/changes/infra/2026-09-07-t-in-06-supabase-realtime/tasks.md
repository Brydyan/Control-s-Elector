# Tasks — T-IN-06: Supabase Realtime

## Fase A: Habilitar Realtime en Supabase

- [ ] **A.1** — En dashboard Supabase, ir a Settings → Realtime
- [ ] **A.2** — Habilitar Realtime si no está
- [ ] **A.3** — Verificar que tabla "acta" está en lista de replicación
- [ ] **A.4** — Verificar tabla "notificacion" está en lista

**Compuerta A:** Dashboard muestra Realtime "Enabled", tablas checked.

## Fase B: Cliente suscripción (Angular)

- [ ] **B.1** — En DashboardComponent (T-FE-11), importar supabase client:
  ```typescript
  import { supabase } from '@core/services/supabase.service';
  ```

- [ ] **B.2** — En ngOnInit, después de GET inicial:
  ```typescript
  this.supabase
    .channel('actas_realtime')
    .on('postgres_changes', 
      { event: '*', schema: 'public', table: 'acta' },
      (payload) => this.handleActaChange(payload)
    )
    .subscribe();
  ```

- [ ] **B.3** — Implementar handleActaChange():
  ```typescript
  handleActaChange(payload) {
    // payload.eventType: 'INSERT' | 'UPDATE' | 'DELETE'
    // payload.new: nueva fila
    // payload.old: fila anterior (si UPDATE)
    
    if (payload.eventType === 'INSERT') {
      this.actas.push(payload.new);
    } else if (payload.eventType === 'UPDATE') {
      const idx = this.actas.findIndex(a => a.id === payload.new.id);
      this.actas[idx] = payload.new;
    }
    
    this.chart.update();
  }
  ```

**Compuerta B:** Compilar sin errores.

## Fase C: Cleanup

- [ ] **C.1** — En ngOnDestroy, remover subscription:
  ```typescript
  ngOnDestroy() {
    this.supabase.removeAllChannels();
  }
  ```

**Compuerta C:** No memory leaks.

## Fase D: Test

- [ ] **D.1** — Abrir dos navegadores: http://localhost:4200/dashboard en cada uno
- [ ] **D.2** — En uno, registrar acta (si T-FE-07 existe)
- [ ] **D.3** — En el otro, verificar que chart actualiza sin refrescar
- [ ] **D.4** — Cambia estado de acta, ambos dashboards se actualizan

**Compuerta D:** Dashboard actualiza en vivo.

## Qué NO hacer

- **No agregar lógica compleja en handleActaChange** — solo actualizar array + chart.update()
- **No filtrar en Realtime** — RLS filtra automáticamente
- **No usar polling también** — Realtime solo

## Compuertas

| Fase | Verifica | Expected |
|------|----------|----------|
| A | Supabase Settings | Realtime Enabled |
| B | Compilación | ng build sin errores |
| C | ngOnDestroy | cleanup presente |
| D | Dos navegadores | chart actualiza en ambos |

## Verificación por Mutación

- **V.1** — Comentar .on() → cambios no se reciben → restaurar
- **V.2** — Deshabilitar Realtime en Supabase → eventos no llegan → restaurar
