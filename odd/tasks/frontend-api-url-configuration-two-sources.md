# ODD — Frontend API URL Configuration (Unify Two Sources)

**Estado:** Propuesta QA Review  
**Fecha:** 2026-11-22  
**Referencia:** Diagnóstico `2026-11-17` (migración Cloudflare Pages + Railway)

---

## Propuesta

**Problema:** El frontend tiene **dos fuentes de verdad conflictivas** para la URL del API:
- `environment.prod.ts` → fija URL al compilar (18 servicios)
- `assets/config.json` → lee URL en runtime (solo Catálogos)

**Síntoma:** En local/Docker, Catálogos llama a **producción** mientras todo lo demás llama a **localhost**. CORS falla, pantalla muestra "No se pudo cargar la configuración electoral".

**Solución:** Unificar en **una sola fuente** (`config.json` en prod + `environment` en local) y limpiar código huérfano.

---

## Scope

| Fase | Tarea | Cambios |
|------|-------|---------|
| **A** | Limpiar `APP_CONFIG` huérfano | Remover `main.ts:35`, eliminar importes (E7) |
| **B** | Actualizar comentarios desactualizados | environment.prod.ts, tasks docs (E9) |
| **C** | Excluir `config.json` del service worker | ngsw-config.json `assetGroup` (E5) |
| **D** | Unificar `ConfigService` en todos los servicios | 18 servicios usan ConfigService, no `environment.apiUrl` |
| **E** | Reescribir documentación de deploy | CLOUDFLARE_PAGES_SETUP.md con valores reales (E6) |
| **F** | Test local: config.json en docker-compose | Verificar que local NO llama a producción |

---

## Checklist

- [ ] **A.1** Remover `APP_CONFIG` token de `main.ts:35`
- [ ] **A.2** Remover importes de `APP_CONFIG` en archivos que no lo usan
- [ ] **B.1** Actualizar comentario en `environment.prod.ts:3` (dice "se inyecta desde config.json", no es verdad hoy)
- [ ] **B.2** Remover referencias a `VITE_API_URL` en docs (task-control/infra.md, T-IN-09.md)
- [ ] **C.1** Verificar que `ngsw-config.json` **excluye** `/assets/config.json` del `assetGroup`
- [ ] **D.1** `ConfigService.getApiUrl()` → exponerlo públicamente
- [ ] **D.2** Actualizar 18 servicios: cambiar `environment.apiUrl` → `ConfigService.getApiUrl()`
- [ ] **D.3** Verificar que todos tienen inyectado `ConfigService` en constructor
- [ ] **E.1** Reescribir `CLOUDFLARE_PAGES_SETUP.md` con path correcto (`www` no `dist/app`)
- [ ] **E.2** Corregir heredoc: usar `<< EOF` sin comillas para expandir variables
- [ ] **E.3** Documentar variables: `API_URL`, `SENTRY_DSN`
- [ ] **F.1** Test local: crear usuario, crear invitación, verificar email (debe llegar)
- [ ] **F.2** Verificar que `ConfiguracionApi` NO llama a producción

---

## Errores Resueltos

| Error | Criticidad | Fix |
|-------|-----------|-----|
| **E2** | 🔴 | config.json apunta a producción siempre → una sola fuente |
| **E4** | 🔴 | Token no se adjunta si URLs difieren → ConfigService unificado |
| **E5** | 🔴 | Service worker sirve config.json vieja → excluir de assetGroup |
| **E6** | 🔴 | Documentación rompe producción → path y variables correctas |
| **E7** | 🟡 | APP_CONFIG huérfano en main.ts → remover |
| **E9** | 🟡 | Comentarios desactualizados → actualizar |

---

## Notas

- **E1** (E3, E8, E10) quedan fuera scope: son detalles que solo importan si E2/E4/E5/E6 no se arreglan.
- **E5 (Service Worker):** Es el más delicado. Si no se excluye `config.json` del cache, el navegador sigue usando la URL vieja aunque cambies el archivo.
- **D.4 (Sentry):** Hoy se inicializa con `environment.sentryDsn`, debería usar `ConfigService.getSentryDsn()`. Fuera de scope, ticket aparte.
- **Local test crítico:** El síntoma original (Catálogos llama a producción) debe desaparecer.

---

## Dependencias

- ✅ No depende de nada
- ⚠️ No bloquea nada
- 📦 Afecta: deploy (Cloudflare Pages), local dev, docker-compose

---

## Riesgo

- **Bajo:** No afecta core logic, es refactor + limpieza + actualización de docs
- **Atención:** Cambios en 18 servicios → fácil de revisar con `git grep environment.apiUrl`

---

## Próximos Pasos

✅ Listo para `/02-builder-odd frontend-api-url-configuration-two-sources`

---

## Referencia Diagnóstico Original

Ubicación: `/home/andy/Documentos/PROYECTOS/CONTROL/Control-s-Elector/docs/FRONTEND_CONFIGURATION_DIAGNOSIS.md`

(o pedir que lo guarde si no existe aún)

