# fixes-required — T-BE-13 · Dashboard

**Severidad:** CRITICAL (gap de cobertura)
**Origen:** Q6 en `verify-report.md` §Q6
**Bloquea el PR:** sí — hasta cerrar el gap, RF-DASH-07 no está probado por mutación.

---

## F1 — RF-DASH-07: el test actual no detecta la reintroducción del patrón de zona horaria

### El problema

El test `RF_DASH_07_novedades_minutosTranscurridosConsistenteConInstant` verifica que el
valor de `minutosTranscurridos` cae en `[0, 1]` para actas recién creadas. Cualquier
implementación que devuelva un número en ese rango pasa — incluyendo la reintroducción del
patrón bug de `T-BE-13.md:374-377` (envolver ambos lados en `ZoneId.systemDefault()`),
porque `Duration.between(ZonedDateTime, ZonedDateTime)` opera sobre los `Instant` subyacentes
y es TZ-invariante cuando ambos lados usan la misma zona.

Medición:

| TZ | Sin mutación | Con mutación `ZoneId.systemDefault()` per T-BE-13.md:374-377 |
|----|---------------|---------------------------------------------------------------|
| UTC | test verde (1/0/0/0) | test verde (1/0/0/0) |
| America/Guayaquil | test verde (1/0/0/0) | test verde (1/0/0/0) |

**Conclusión:** la garantía de RF-DASH-07 ("el resultado no depende de dónde se calcula") está
implementada correctamente en `DashboardService.novedades()` (sólo `Instant.now()` +
`Duration.between(Instant, Instant)`, sin `ZoneId.systemDefault()`), pero **no está probada
por mutación**. Una mano distraída que añada `ZoneId.systemDefault()` per T-BE-13.md:374-377
no romperá CI.

### Fix propuesta

**Objetivo:** que el test caiga con la reintroducción del bug per T-BE-13.md:374-377 (y
variantes asimétricas más agresivas), corriendo en ambas TZs.

**Estrategia:** añadir un test que cree un acta con `fechaRegistro = ayer` (en zona local)
usando `JdbcTemplate` para fijar la columna directamente. Esto expone la asimetría porque:

- Implementación correcta (`Duration.between(Instant, Instant)`): ~1440 minutos, igual en UTC y GYE.
- Implementación bug per T-BE-13.md:374-377: idéntico al correcto (mismo número), no la detecta.
- Implementación bug más agresiva (e.g. `LocalDate.now() * 1440` o redondeo a día local): difiere
  entre UTC y GYE en exactamente las horas de offset (5 horas = 300 minutos en este caso).

La nueva mutación a probar (variante que el test debería cazar):
```java
long minutos = Duration.between(
    fechaRegistro.atZone(ZoneId.systemDefault()).toLocalDate().atStartOfDay(ZoneId.systemDefault()),
    Instant.now().atZone(ZoneId.systemDefault()).toLocalDate().atStartOfDay(ZoneId.systemDefault())
).toMinutes();
```

### Implementación sugerida

1. **Inyectar `JdbcTemplate` en el test** vía `@Autowired JdbcTemplate jdbcTemplate`.
2. **Reemplazar el test actual** `RF_DASH_07_novedades_minutosTranscurridosConsistenteConInstant`
   por **dos tests** (uno con `fechaRegistro = ayer 23:00 UTC` y otro con `fechaRegistro = ayer 04:00 UTC`),
   cada uno afirmando que el resultado es independiente de la TZ del JVM. Para ello:
   - Calcular `expected` usando la fórmula correcta (`Duration.between(fechaRegistro, Instant.now()).toMinutes()`).
   - Afirmar **exactamente** `expected` (sin tolerancia), no rango `[expected, expected+1]`.
3. **Verificar ambas TZs** como hasta ahora: una corrida con `TZ=UTC`, otra con `TZ=America/Guayaquil`,
   ambas deben pasar.
4. **Aplicar mutación D.5+** (la exacerbada) y verificar que **cae** en al menos una TZ.

### Por qué NO incluir en `apply-progress.md` ahora

Esto es una fix de cobertura de tests — el comportamiento del código sí cumple RF-DASH-07
(verificado leyendo `DashboardService.novedades()` líneas 169-198). Sólo el test es demasiado
tolerante. La fix es de 1 archivo de test, ~30 líneas, no toca código de producción.

### Estimación

- 1 archivo modificado: `DashboardControllerTest.java`
- 2 tests nuevos (o 1 test con 2 invocaciones parametrizadas)
- Re-medir el conteo de tests: pasa de 22 a 24
- Tiempo: ~20 min de TDD (red → green → refactor) + 5 min de mutación D.5+

### Criterio de cierre

1. Test verde con `TZ=UTC` y con `TZ=America/Guayaquil` (código actual sin mutación).
2. Mutación `ZoneId.systemDefault()` per T-BE-13.md:374-377 reintroducida → **cae** en al menos
   una TZ (con `fechaRegistro = ayer`).
3. Mutación exacerbada (`LocalDate.atStartOfDay * 1440`) → **cae** en al menos una TZ.
4. Compuerta final: 268 (o 270 con los nuevos tests) / 0 / 0 / 2 / BUILD SUCCESS.
5. `git status --short` limpio respecto al working tree actual (sin nuevos archivos en el árbol).

---

## Lo que NO bloquea (pero queda anotado)

| # | Hallazgo | Severidad | Acción |
|---|----------|-----------|--------|
| F2 | Q1–Q5 (ANULADA en queries, DISTINCT, RBAC, sólo GET) | PASS | Ninguna |
| F3 | Línea base coincide con Builder | — | Ninguna |
| F4 | `DashboardControllerTest` con 22 tests verdes | — | Ninguna |
| F5 | CI runner pasa | — | Ninguna |
| F6 | Actividad paralela de otro agente (`002-rbac-seed.sql` modificado, `MigracionesSchemaTest` committeado) | INFO | Ninguna — fuera del scope de T-BE-13 |
| F7 | Discovery #1: `motivo_disputa` no existe en `Acta.java` ni en changelog Liquibase | INFO | Otro change |
| F8 | Discovery #2: geografía/Cargo/Candidato/Partido sin `publicId` | INFO | Otro change |
