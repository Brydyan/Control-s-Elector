# Task Control — Control Electoral

Gestión de tareas del proyecto. Una tarea por módulo.

---

## Archivos

| Archivo | Área |
|---------|------|
| `backend.md` | Quarkus — entidades, repositorios, casos de uso, API REST, seguridad |
| `frontend.md` | Angular — componentes, guards, vistas por rol |
| `infra.md` | Docker, Railway, Cloudflare Pages, Neon, R2, CI/CD |

---

## Formato de tarea

```
### T-[AREA]-[NUM] — [Título corto]

**Estado:** TODO | EN PROGRESO | HECHO | BLOQUEADO
**Prioridad:** ALTA | MEDIA | BAJA
**Asignado:** [nombre / sin asignar]
**Depende de:** T-XX-XX (si aplica)

[Descripción breve de qué hacer y por qué]

- [ ] Sub-tarea 1
- [ ] Sub-tarea 2
```

---

## Estados

| Estado | Significado |
|--------|-------------|
| `TODO` | Pendiente, no iniciada |
| `EN PROGRESO` | Alguien trabajando activamente |
| `HECHO` | Completa, código en `main` |
| `BLOQUEADO` | Espera dependencia externa |

## Prioridades

| Prioridad | Criterio |
|-----------|----------|
| `ALTA` | Bloquea otras tareas o es MVP |
| `MEDIA` | Necesaria pero no urgente |
| `BAJA` | Mejora, nice-to-have |
