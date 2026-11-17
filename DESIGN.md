---
name: Control Electoral
description: Sistema de control, registro y validación de actas electorales en tiempo real para Santa Elena
colors:
  primary: "#2563eb"
  primary-strong: "#1d4ed8"
  navy-900: "#0f172a"
  surface-page: "#f8fafc"
  surface-card: "#ffffff"
  surface-sidebar: "#0f172a"
  text-primary: "#0f172a"
  text-secondary: "#334155"
  text-muted: "#475569"
  text-on-brand: "#ffffff"
  status-success: "#065f46"
  status-warning: "#92400e"
  status-danger: "#991b1b"
  border-subtle: "#f1f5f9"
  border-medium: "#e2e8f0"
typography:
  display:
    fontSize: "56px"
    fontWeight: 800
    lineHeight: 1.1
  title:
    fontSize: "24px"
    fontWeight: 600
    lineHeight: 1.25
  body:
    fontSize: "14px"
    fontWeight: 400
    lineHeight: 1.5
  meta:
    fontSize: "12px"
    fontWeight: 400
rounded:
  sm: "6px"
  md: "10px"
  lg: "14px"
  xl: "18px"
  pill: "999px"
spacing:
  1: "4px"
  2: "8px"
  3: "12px"
  4: "16px"
  5: "20px"
  6: "24px"
  8: "32px"
  10: "40px"
  12: "48px"
  16: "64px"
---

## Overview

Sistema de alta confiabilidad y misión crítica para veedores, coordinadores y administradores electorales. Diseñado para garantizar legibilidad instantánea bajo estrés, precisión matemática en escrutinios y navegación ágil en dispositivos móviles y de escritorio.

## Colors

- **Primary Brand:** `#2563eb` (azul institucional Tailwind/Blue-500) con énfasis en `#1d4ed8` (Blue-600).
- **Surfaces:** `#f8fafc` (Slate-50) para fondo general de la aplicación, `#ffffff` para tarjetas elevadas, `#0f172a` (Slate-900 profundo) para sidebar y paneles de alto contraste.
- **Status Roles (WCAG AA Compliant):**
  - Success: `#065f46` con fondo `#ecfdf5` y borde `#a7f3d0` (actas completadas, aprobadas).
  - Warning: `#92400e` con fondo `#fffbeb` y borde `#fde68a` (actas en disputa, pendientes).
  - Danger: `#991b1b` con fondo `#fef2f2` y borde `#fecaca` (errores matemáticos, actas anuladas).

## Typography

- **Escala:** Display (56px), Title (24px), Body (14px), Meta (12px).
- **Tratamiento numérico:** Monospace o tabular figures para conteo de votos, porcentajes de avance y mesas electorales para prevenir saltos de línea y desalineación.

## Layout

- **Desktop:** Barra lateral fija (`240px`), topbar de estado (`56px` o `64px`) con indicador de conexión en vivo y área de trabajo fluida con scroll independiente.
- **Mobile:** Adaptación fluida de 1 columna con targets táctiles mínimos de 44x44px.

## Elevation & Depth

- Tarjetas estándar: `box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05)`.
- Elementos modales o flotantes: `box-shadow: 0 8px 24px rgba(0, 0, 0, 0.1)`.

## Shapes

- Esquinas suavizadas funcionales con radios de 6px (sm), 10px (md), 14px (lg) y pill para etiquetas de estado.

## Components

- **Badges de estado:** Pastillas con fondo translúcido y texto en alto contraste que indican el ciclo de vida del acta (`BORRADOR`, `ENVIADA`, `EN_DISPUTA`, `CORREGIDA`, `APROBADA`).
- **Botones y controles:** Botones con altura táctil accesible (mínimo 40px en desktop, 44px en mobile).

## Do's and Don'ts

- **DO:** Priorizar siempre la claridad de los números y el estado del acta sobre efectos decorativos.
- **DO:** Mantener indicadores visuales de conectividad y estado en tiempo real siempre visibles.
- **DON'T:** Usar textos en bajo contraste sobre fondos oscuros o tarjetas.
- **DON'T:** Ocultar validaciones de error matemático detrás de scrolls largos o modales invasivos.
