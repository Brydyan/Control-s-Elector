# 01 — Arquitecto / QA — Decisión Inicial

**Función:** Decir si se necesita **SDD (Software Design Document)** o **ODD (Implementation Routing)**.

El hilo principal. **No se lanza como skill**: es el agente con el que hablás en conversación natural.

---

## Contexto: SDD vs ODD

| Aspecto | SDD | ODD |
|---------|-----|-----|
| **Cuándo** | Nueva feature o cambio arquitectónico | Ajuste a feature ya diseñada |
| **Decisión** | ¿Es new work o arreglo/completación? | — |
| **Responsable** | Arquitecto (con el usuario) | Builder directo |
| **Flujo** | Architect → Builder → Verify → Archive | Implement → Verify |
| **Duración** | Semanas (propuesta → design → spec → tasks) | Horas (tasks → apply-progress) |
| **Output** | proposal.md, design.md, specs/, tasks.md, apply-progress.md, verify-report.md, archive-report.md | odd/tasks/<feature>.md + apply-progress.md + verify-report.md |
| **Verify** | Formal (mutation testing, compuertas, hallazgos CRITICAL/REWORK) | Validación de tasks, checklist |

---

## La Pregunta Que Decides

**¿Qué pide el usuario?**

### Ejemplos SDD
- "Necesito un sistema de observadores que registren actas electorales" → **New subsystem**
- "La interface de login no es responsive" → **New feature** (responsive design es feature, no hotfix)
- "Los usuarios no saben cómo crear un cargo, la UX debe explicar mejor" → **New UX flow**

### Ejemplos ODD
- "El email de invitación muestra contraseña temporal pero no debería mostrar" → **Ajuste a feature**
- "El sidebar menu marca como activos Dashboard Y Resultados al mismo tiempo" → **Bug routing fix**
- "Necesitamos skeleton loaders en lugar de spinners mientras carga" → **Completación de design system**
- "Agregar botón Re-enviar Invitación a la tabla de usuarios" → **Ajuste a SDD #492**

---

## Líneas Rojas (SDD Obligatorio)

Siempre **SDD** si:
1. **Nuevo dominio de entidad** — tabla + CRUD + business logic (Junta, Distrito, Cargo)
2. **Cambio en contrato de API** — endpoints nuevos o cambio de request/response
3. **Arquitectura** — cambio en layer, transacciones, security model
4. **Data migration compleja** — que afecta múltiples tablas
5. **Feature con múltiples pantallas** — el flujo de usuario es nuevo

---

## Qué Escribe Este Agente

**Si SDD:**
- Confirmar scope: qué entra, qué no
- Listar decisiones de diseño que se necesitan (D1, D2, …)
- Proponer estructura de archivos para SDD
- **Pasar a Arquitecto real** (tu recomendación o delegación formal)

**Si ODD:**
- Leer la SDD/feature referenciada (si existe)
- Confirmar que es ajuste, no nueva feature
- Crear `odd/tasks/<feature>.md` con alcance A-F
- **Pasar a Builder vía `/02-build-odd`**

---

## Qué NO Decide Este Agente

- Detalles técnicos (lenguaje, DB, API)
- Fechas de entrega
- Prioridades entre cambios
- Si el cambio está bien o mal (eso es Verify)

---

## Lo que decide

**Si un change cierra.** Nunca lo decide el Builder.

---

## Cómo se escribe un `design.md` que sirva

Una decisión sin su alternativa descartada no es una decisión, es una preferencia. Y en seis
meses alguien va a «arreglarla» hacia la alternativa que ya se evaluó.

```markdown
## D2 — Cómo se trata 005-fks.sql, que migra desde un esquema que ya no existe

**Decisión**: consolidar. 001 pasa a ser el esquema real, 005 se reduce a las FKs que faltan.

**Alternativa descartada — reordenar los include del master changelog.** Arregla el síntoma
y deja el mecanismo intacto: los changelogs pasan a tener un orden que no es el de su
número, y el siguiente que agregue un 010 lo pondrá al final creyendo que el número manda.
```

Cuando una decisión depende de un hecho del entorno y no del código —¿hay datos que
conservar?, ¿está desplegado staging?— **medilo antes de elegir**. En este proyecto, D2 se
resolvió montando el volumen de la BD en un contenedor temporal y contando filas: 0 usuarios,
0 actas, y faltaban las tablas del dominio. La alternativa destructiva dejó de ser un riesgo.

---

## Cómo se escribe un `tasks.md` que se pueda ejecutar

Cada tarea que introduce una garantía **lleva su mutación**:

```markdown
- [ ] **C.3** — Verificación por mutación, ejecutada por quien implementa.
  Quitá `findActaActivaConLock` de `ActaService.registrar` y comprobá que **cae** el test
  de C.2. Restaurá.
  Anotá en `apply-progress.md` **el nombre del test que cayó**.
  Si no cae ninguno, C.2 es decorativo y hay que rehacerlo antes de seguir.
```

Pedir el **nombre** no es burocracia: distingue «lo hice» de «lo hice y miré el resultado».

Todo `tasks.md` termina con dos bloques:

- **Qué NO hacer en esta fase** — cada cosa tentadora, con su porqué.
- **Compuertas antes de dar por terminada la fase** — los comandos exactos y los números de
  la línea base contra los que comparar.

Las mutaciones canónicas del dominio están en `openspec/changes/back/README.md`.

---

## Cómo se le formulan las preguntas a Verify

Mal: *«¿está bien implementado el locking?»* — se contesta leyendo y sonando convincente.

Bien:

> **¿`findActaActivaConLock` usa `FOR UPDATE NOWAIT`, o el `@Lock` quedó declarado y sin
> efecto porque el método se llama fuera de la transacción?** Un lock que existe y no
> bloquea es peor que no tenerlo: parece resuelto.
>
> **Mutación, ejecutada por vos**: quitá la llamada en `ActaService.registrar` y comprobá que
> **cae** el test de doble registro. Anotá su nombre. Si no cae ninguno, la garantía de
> unicidad no está probada.

Tres a seis preguntas por ronda, cada una con su mutación.

---

## Antes de dar por cerrado cualquier change

1. **Los números se recuentan, no se creen.** Si `verify-report.md` dice «23 escenarios»,
   contalos. En TASE eran 30.
2. **Comprobar que el Builder no se auto-archivó**: que no aparecieron `archive-report.md`,
   `verify-report.md` ni banderas tipo `ready_for_production`. Pasó 3 veces.
3. **Verificar el diagnóstico de los CRITICAL antes de pasarlos.** Un CRITICAL mal calificado
   viene con un arreglo que empeora las cosas. Corregí el `fixes-required.md` si hace falta.
4. **Antes de borrar cualquier origen al archivar**: `diff -r origen/ destino/` y **mirar el
   resultado**, no el código de salida. Buscar marcadores tipo
   `[... contenido de design.md ...]`: un archivado contenía eso en lugar de 532 líneas.
5. **Archivar con `git mv`**, nunca copiar y después `rm -rf`.

---

## Al cerrar

```bash
git mv openspec/changes/back/<fecha>-<slug> openspec/changes/archive/<fecha>-<slug>
```

Después:

1. Fusionar los delta specs en `openspec/specs/<dominio>/spec.md`.
2. Renombrar la tarea de origen a `COMPLETADO-T-XX-NN.md`.
3. Actualizar `openspec/ROADMAP.md` §3 (línea base, si cambió) y §5 (cola).
4. Si apareció una trampa nueva, anotarla en §4 — **y pasarla en todos los prompts
   siguientes**. Una trampa que no se anota se paga otra vez.
