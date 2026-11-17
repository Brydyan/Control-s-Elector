# openspec/changes — convención

Un *change* es una carpeta con todo lo que se sabe sobre un trabajo. Se separan por área
porque backend, frontend e infra tienen compuertas distintas, ritmos distintos y a menudo
personas distintas.

```
openspec/changes/
  back/    ← Spring Boot · backend/electoral-api/     · fuente: documents/task-control/backend.md
  front/   ← Angular+Ionic · frontend/app/            · fuente: documents/task-control/frontend.md
  infra/   ← Docker, Railway, Vercel, Supabase, CI/CD · fuente: documents/task-control/infra.md
  archive/ ← changes cerrados, EN PLANO (sin subcarpetas por área)
```

## Nombre de carpeta

```
<área>/<YYYY-MM-DD>-<slug>/
```

La fecha es la de **apertura** del change y **no cambia nunca** — tampoco al archivar. Un
change abierto en agosto y cerrado en octubre se archiva con su fecha de agosto; si no, se
pierde cuánto tardó.

## Qué lleva dentro

| Archivo | Responde a | Lo escribe |
|---|---|---|
| `proposal.md` | Qué se construye y por qué. Alcance y **fuera de alcance** | Arquitecto |
| `design.md` | Decisiones (D1, D2…) **con las alternativas descartadas y su motivo** | Arquitecto |
| `specs/<dominio>/spec.md` | Requisitos y escenarios en Gherkin flojo (GIVEN/WHEN/THEN) | Arquitecto |
| `tasks.md` | Casillas ejecutables, con sus mutaciones y sus trampas | Arquitecto |
| `apply-progress.md` | Qué se hizo y qué se **midió** | Implementador |
| `verify-report.md` | Qué se ejecutó y qué se encontró | Verificador |
| `fixes-required.md` | Sólo lo que bloquea, con reproducción | Verificador |
| `archive-report.md` | La historia completa, para quien la lea en un año | Arquitecto |

## Archivado

```bash
git mv openspec/changes/back/2026-09-07-fix-liquibase-rbac-order \
       openspec/changes/archive/2026-09-07-fix-liquibase-rbac-order
```

**En plano**: `archive/` no repite las subcarpetas por área. El prefijo `T-BE-`/`T-FE-`/`T-IN-`
del change ya dice de dónde vino, y un archivo plano se recorre de un vistazo.

Reglas que costaron trabajo perdido (§7 del playbook):

- Archivar es **`git mv`**, nunca copiar y después `rm -rf`. El agente de archivado copió en
  vez de mover 4 veces.
- Antes de borrar cualquier origen: `diff -r origen/ destino/` y **mirar el resultado**, no
  el código de salida.
- Buscar marcadores tipo `[... contenido de design.md ...]` antes de dar por buena una copia.
  Un archivado contenía eso en lugar de 532 líneas reales.

## Un change por tarea, salvo frontera compartida

La regla por defecto es un change SDD por tarea `T-XX-NN` de `documents/task-control/`.

La excepción: cuando dos tareas comparten una **frontera** —lo que una produce es lo que la
otra consume— van juntas. Si se separan, cada lado se prueba con el otro simulado y nadie
prueba que encajen (§4.1 del playbook). Ejemplo vivo aquí: el DTO que el frontend envía a
`POST /api/v1/observer/actas` y el `ActaRequestDto` que el backend espera.

## Al cerrar

1. `verify-report.md` sin CRITICAL abiertos, con sus números **recontados** por el Arquitecto.
2. Delta specs fusionados en `openspec/specs/<dominio>/spec.md`.
3. `git mv` a `archive/`.
4. Renombrar la tarea de origen a `COMPLETADO-T-XX-NN.md` — es la convención del repo.
5. Actualizar `openspec/ROADMAP.md` §5 (cola de trabajo) y §3 (línea base, si cambió).
