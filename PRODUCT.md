# Product

<!-- impeccable:product-schema 1 -->

## Platform

adaptive

## Users

1. **OBSERVER (Veedor/a Político/a de Campo):** En recintos electorales de Santa Elena con teléfonos móviles, bajo conectividad inestable, alta presión de tiempo y ruido. Su trabajo es registrar actas de JRVs, transcribir votos por candidato/dignidad y capturar fotografías nítidas y optimizadas como evidencia.
2. **COORDINATOR (Coordinador/a Operativo/a):** Operando desde puestos de control cantonal/provincial o computadoras/tablets. Su labor es auditar inconsistencias, gestionar actas en disputa, contrastar transcripción contra evidencia fotográfica y validar/aprobar actas.
3. **MASTER (Administrador/a del Sistema Electoral):** Configuración previa y supervisión crítica. Define cargos, candidaturas, recintos/JRVs, gestiona usuarios y permisos, resuelve anulaciones y audita sesiones.
4. **ADMIN (Observador/a Directivo/a o Monitoreo):** Acceso analítico de solo lectura. Monitorea tableros consolidados, progreso de escrutinio cantonal y resultados en tiempo real.

## Product Purpose

Garantizar la transparencia, velocidad y defensa del voto en las Elecciones Seccionales de Santa Elena (2027-2031) mediante la digitalización descentralizada, validación matemática inmediata y respaldo fotográfico inmutable de las actas de escrutinio.

## Positioning

Control Electoral no es un simple formulario web ni un dashboard estático: es un sistema de defensa electoral en tiempo real con validación estricta de coherencia matemática de actas (sufragantes vs votos válidos, blancos y nulos), soporte para contingencia operativa y trazabilidad fotográfica forense por dignidad electoral.

## Operating Context

- **Jornada Electoral:** Uso intensivo en pocas horas críticas de la tarde/noche electoral.
- **Dispositivos móviles diversos:** Pantallas de gama media/baja usadas por veedores bajo luz artificial deficiente o intemperie.
- **Centros de Cómputo:** Pantallas grandes y tablets para coordinadores y directivos.
- **Conectividad variable:** Requisitos de carga ágil, compresión WebP en cliente y feedback táctil/visual inmediato.

## Capabilities and Constraints

- **Seguridad RBAC:** Redirecciones estrictas según rol JWT (`OBSERVER`, `COORDINATOR`, `MASTER`, `ADMIN`).
- **Arquitectura:** Frontend Angular 21 + PrimeNG 21 (Standalone Components), Backend Quarkus 3.40 / Java 25 con PostgreSQL y almacenamiento S3/Supabase.
- **Ciclo de vida del acta:** `BORRADOR` → `ENVIADA` → `EN_DISPUTA` → `CORREGIDA` → `APROBADA` → `ANULADA`.
- **Estructura territorial:** Provincia de Santa Elena dividida en Cantones (Santa Elena, La Libertad, Salinas) con dignidades provinciales, cantonales y parroquiales.

## Brand Commitments

- **Nombre oficial:** Control Electoral — Provincia de Santa Elena 🇪🇨.
- **Tono institucional:** Serio, seguro, transparente, de máxima fiabilidad, con alta legibilidad bajo situaciones de estrés y cansancio visual.

## Evidence on Hand

- Documentación arquitectónica y operacional en `README.md`.
- Rutas y guardias RBAC implementados en `frontend/app/src/app/app.routes.ts`.
- Sistema de diseño propio sobre PrimeNG: preset y tokens en `frontend/app/src/theme/`.
- Suite E2E de navegador en `frontend/app/e2e` (Playwright) y suite de contrato
  HTTP en `backend/contract-tests`, que corren contra la misma API de producción.

## Product Principles

1. **Claridad sobre adorno:** En un conteo electoral cada dígito cuenta; el contraste, la tipografía tabular y la legibilidad son sagrados.
2. **Cero fricción en campo:** El ingreso de datos del veedor debe requerir la menor cantidad de taps y ofrecer confirmación táctil y visual inequívoca.
3. **Inmediatez en la detección de discrepancias:** Resaltar visualmente alertas matemáticas (sumatorias que no cuadran con el padrón) antes de enviar el acta.
4. **Resiliencia operativa:** Interfaces que transmitan solidez técnica y toleren latencia o reconexiones sin perder datos cargados.
5. **Jerarquía clara por rol:** Cada usuario ve exactamente lo que necesita para su función sin ruido ni confusión de permisos.

## Accessibility & Inclusion

- Contraste accesible (WCAG AA mínimo) indispensable para uso nocturno en recintos.
- Targets táctiles mínimos de 44x44px para uso con una sola mano en campo.
- Soporte visual prioritario para lectura de números (fuentes tabulares y campos numéricos amplios).
