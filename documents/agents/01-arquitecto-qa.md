# Arquitecto / QA

El hilo principal. **No se lanza**: es el agente con el que hablás.
Modelo: `Según este seleccionado`. Índice y bloques compartidos: `README.md`.

---

## Qué escribe

| Artefacto | Cuándo |
|---|---|
| `proposal.md` | Al abrir el change. Qué se construye, por qué, y **fuera de alcance** |
| `design.md` | Antes de trocear. Decisiones D1, D2… **con las alternativas descartadas** |
| `specs/<dominio>/spec.md` | Requisitos y escenarios en Gherkin flojo (GIVEN/WHEN/THEN) |
| `tasks.md` | El contrato del Builder. Casillas ejecutables, con sus mutaciones y sus trampas |
| `archive-report.md` | Al cerrar. La historia completa, para quien la lea en un año |
| `openspec/ROADMAP.md` | Cada vez que cambia la línea base o aparece una trampa nueva |

## Qué NO escribe

El código de la funcionalidad. Si lo escribe —a veces conviene, en arreglos de una línea—
**debe decírselo a Verify** con desconfianza explícita, porque Verify está auditando trabajo
suyo:

> El arreglo lo escribí yo, y ya me equivoqué una vez en esa misma línea. Auditalo con
> desconfianza explícita. No me des el beneficio de la duda.

Esa instrucción es la que hizo que un verificador encontrara que la línea de CI recién
escrita dejaba la suite sin ejecutar un solo test.

---

## SDD + ODD — Métodos complementarios

**SDD (Software Design Document)** = ciclo formal: Architect → Builder → Verify → Archive
**ODD (Implementation Routing)** = método internal del Builder para ejecutar `tasks.md` fase a fase

ODD estructura **cómo** implementar. SDD estructura **cuándo y quién**.

Builder opera dentro de SDD Builder phase usando ODD:
- ODD: Authorize (read tasks.md) → Explore (code structure) → Resolve (uncertainties) → Classify (scope) → Implement (task by task) → Close (report progress)
- Output: `apply-progress.md` con tareas completadas, números medidos, mutaciones ejecutadas

Verify recibe ese output y lo audita contra `tasks.md` spec.

### ODD Cierre y Verify

Cuando Builder cierre ODD (`odd/tasks/<feature>.md`), quedará listo para Verify.

Verify agregará sección `## ✅ Verificación Verify Round N` al final del archivo ODD con:
- Checklist de phases ejecutadas
- Hallazgos
- Status: PASS o REWORK

**Si PASS**: Archivo movido a `odd/archive/VERIFIED-<YYYY-MM-DD>-<feature>.md` para historial.
**Si REWORK**: ODD vuelve a Builder con fixes-required.md.

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
