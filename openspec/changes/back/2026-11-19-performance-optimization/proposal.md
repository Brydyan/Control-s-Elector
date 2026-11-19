# SDD — Performance Optimization: Indexing, Caching & Infrastructure

**Referencia:** T-PERF-OPT-01  
**Epic:** Infra › Escalabilidad & Performance  
**Componentes:** Backend (BD + ORM), Frontend (Caching), Infraestructura (Compression + Pool)  

---

## Propuesta

Optimizar **arquitectura de performance** para soportar **1410 usuarios simultáneos** y **50K actas** sin degradación.

**Hoy:**
- Falta índices críticos → Full table scans O(n) en actas
- Eager loading en Usuario.rol → N+1 queries (21 queries para listar 20 usuarios)
- Frontend sin caché → Requests duplicadas (partidos, resultados)
- Sin compresión HTTP → Banda ancha desperdiciada (1 MB → 100 KB con gzip)
- Pool 20 conexiones → Colapso bajo carga (1410 usuarios necesitan 50+)

**Resultado actual (20 datos, 1 usuario):**
- Login: 2.73s, Listar actas: 1.5-2.7s, Dashboard: 3-5s

**Resultado proyectado en producción:**
- **Sin optimizaciones:** 10-30s por operación (colapso a 100 usuarios)
- **Con optimizaciones:** 0.5-2s por operación (soporta 1410 usuarios)

---

## Dominio del Problema

### Análisis de Impacto

| Problema | Causa | Impacto Hoy | Impacto Prod (50K actas) | Usuarios Afectados |
|----------|-------|------------|------------------------|--------------------|
| Falta índices `acta(*)` | Full table scan O(n) | 1-2s | **5-10s+** | Todos (Observer, Coordinator) |
| Falta índices `acta_detalle(*)` | Full table scan en JOIN | 2-3s | **20-30s+** | Dashboard users |
| N+1 Usuario.rol | Eager load | 21 queries/page | 1410 queries/page | Admin users |
| Sin caché partidos | Requests duplicadas | 2.5s × cargos | mismo | Observers |
| Sin caché dashboard | Requests duplicadas | 1.69s × tabs | mismo | Dashboard users |
| Sin gzip | Tamaño sin comprimir | 800ms en 4G | 800ms (peor) | Mobile users |
| Pool 20 conexiones | Espera en cola BD | Latencia bajo carga | **500-2000ms por usuario** | Todos bajo pico |

---

## Scope

### A. Database Indexing (Performance crítica)

**Tabla `acta`:**
- Crear índices simples: `registrada_por`, `estado`, `jrv_id`, `cargo_id`, `fecha_registro DESC`
- Crear índice compuesto: `(jrv_id, cargo_id, estado)` para queries complejas
- Verificar con EXPLAIN ANALYZE que se usan (no seq scan)

**Tabla `acta_detalle`:**
- Crear índices: `acta_id`, `candidato_id`
- Para optimizar LEFT JOIN en dashboard

**Tabla `usuario`:**
- Ya existe índice en `email_control` ✓
- Verificar índice en `rol_id` para Eager→Lazy migration

---

### B. Backend: ORM & Query Optimization

**Cambio Eager → Lazy Loading:**
- `Usuario.java`: `@ManyToOne(fetch = FetchType.LAZY)` en campo `rol`
- Validar que no causa LazyInitializationException (ver testing)

**Batch Endpoints (Evitar N+1):**
- Crear `/api/v1/observer/cargos-with-candidatos?jrvId={id}` que retorna:
  ```json
  {
    "cargos": [
      {
        "id": 1,
        "nombre": "Presidente",
        "candidatos": [...],
        "partidos": [...]
      }
    ]
  }
  ```
- Reemplazar 3 endpoints separados por 1 (reduce requests 66%)

**Native Queries Optimizadas:**
- Dashboard `/resultados`: Review EXPLAIN ANALYZE, agregar hints si necesario
- ActaRepository queries: Usar índices creados

---

### C. Frontend: Caching Strategy

**Service-level Caching:**
- `observer.api.ts`: Cachear `getPartidos()` con `shareReplay(1)`
- `dashboard.api.ts`: Cachear `getResultados()` con `shareReplay(1)`
- Usar RxJS `shareReplay(1)` para reutilizar subscriptions

**HttpClient Interceptor (Opcional):**
- Implementar cache decorator para GET requests
- TTL configurable (default 5 minutos)

---

### D. Infrastructure: Compression & Connection Pool

**HTTP Compression (gzip):**
- Habilitar en `application.properties`: `quarkus.http.enable-compression=true`
- Configurar nivel 6 (balance CPU vs ratio): `quarkus.http.compression-level=6`
- Mínimo 1024 bytes: `quarkus.http.min-gzip-size=1024`

**Connection Pool:**
- Aumentar de 20 (default) a 50 (producción): `quarkus.datasource.max-size=50`
- Min-size 10: `quarkus.datasource.min-size=10`
- Timeout adecuado: `quarkus.datasource.jdbc.acquisition-timeout=30s`

---

### E. Testing & Verification

**Backend Tests:**
- Verificar N+1 eliminado en `UsuarioResourceIT` (contar queries)
- Verificar índices usados: EXPLAIN ANALYZE en cada query crítica
- Load test: 100 usuarios simultáneos → debe ser <2s por operación

**Frontend Tests:**
- Verificar caché funciona: mismo endpoint llamado 2 veces → 1 HTTP request
- Verificar batch endpoint retorna estructura correcta

**Integration Tests:**
- E2E: Observer → login → ver actas → registrar acta → debe ser <3s total
- Dashboard: load → cambiar tabs → no hace requests nuevas (caché)

---

### F. Documentation & Migration Guide

- `PERFORMANCE_OPTIMIZATION.md`: Guía completa de cambios
- `INDEXING_GUIDE.md`: Cuándo crear índices nuevos
- `CACHING_STRATEGY.md`: Patrón frontend para caché
- Migration guide: pasos para aplicar en producción

---

## Decisiones de Diseño

**D1 — ¿Índices vs Query Optimization?**
- Opción A: Solo optimizar queries (usar joins mejor)
- Opción B: Crear índices (simple, impacto inmediato)
- **Elegida:** B. Índices son line 1 performance improvement. Luego optimizar queries.

**D2 — ¿Lazy loading para TODAS las relaciones ORM?**
- Opción A: SÍ. Cambiar a LAZY en todas (less memory, more queries si no se optimiza)
- Opción B: Selectivo. Solo donde se sabe que causa N+1
- **Elegida:** B. Cambiar `Usuario.rol` → LAZY. Revisar otras relaciones caso por caso.

**D3 — ¿Caché Frontend: Interceptor vs Service-level?**
- Opción A: HttpClient Interceptor (global, automático)
- Opción B: Service-level con shareReplay (explícito, controlado)
- **Elegida:** B. Más predecible. Fase 2: considerar interceptor si patrones se repiten.

**D4 — ¿Batch Endpoint para Cargos+Candidatos+Partidos?**
- Opción A: Mantener 3 endpoints, cachearlos (simple pero menos eficiente)
- Opción B: Crear endpoint batch nuevo (más eficiente, cambio de API)
- **Elegida:** B. Crear `/cargos-with-candidatos` como endpoint nuevo. Mantener viejos para compatibilidad.

**D5 — ¿Connection Pool size: 50 vs 100?**
- Opción A: 50 (conservador, 25 usuarios por conexión en pico)
- Opción B: 100 (agresivo, pero más memoria)
- **Elegida:** A. 50 es suficiente. Monitorear y aumentar si se alcanza límite en producción.

---

## Cambios de Arquitectura

### Layer de Persistencia (BD)

```
BEFORE:
  SELECT * FROM acta WHERE registrada_por = ? → Full table scan O(n)
  
AFTER:
  SELECT * FROM acta WHERE registrada_por = ? → Index scan O(log n)
  Reduction: 50,000 / log(50,000) = 3,125× faster
```

### Layer de ORM (Hibernate)

```
BEFORE:
  for (Usuario u : usuarios) {
    LazyInitializationException if accessing rol outside transaction
  }
  
AFTER:
  @ManyToOne(fetch = FetchType.LAZY)  // Load only when accessed
  → 1 query for usuarios + N queries for roles = more control
```

### Layer de API (Frontend)

```
BEFORE:
  GET /partidos → 1st call: HTTP request (565ms)
  GET /partidos → 2nd call: HTTP request (566ms) ← DUPLICATE
  
AFTER:
  GET /partidos → 1st call: HTTP request (565ms)
  GET /partidos → 2nd call: Cache hit (1ms)
  Savings: 565ms per duplicate
```

### Layer de Network (Infrastructure)

```
BEFORE:
  Response: 500 KB (no compression)
  Network time (4G): 400ms
  
AFTER:
  Response: 50 KB (gzip level 6)
  Network time (4G): 40ms
  Savings: 360ms per large response
```

---

## Próximos Pasos

✅ **Documento SDD listo en:** `/openspec/changes/back/2026-11-19-performance-optimization/`

→ **Pasar a Arquitecto para feedback** en D1-D5:
- D1: ¿Index-first o query-optimization-first?
- D2: ¿Lazy load solo Usuario.rol o revisar todas?
- D3: ¿Service-level caché o interceptor + service?
- D4: ¿Crear batch endpoint nuevo o mantener 3 separados?
- D5: ¿Pool 50 o 100?

**Impacto estimado post-implementación:**
- **Latencia:** 10-15s → 0.5-2s (87% mejora)
- **Throughput:** 100 req/s → 1000 req/s (10× mejor)
- **Usuarios soportados:** ~100 → 1410 (14× más escalable)
