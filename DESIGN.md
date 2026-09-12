---
name: Control Electoral
description: Sistema de control, registro y validación de actas electorales en tiempo real para Santa Elena
colors:
  primary: "#3880ff"
  primary-strong: "#5260ff"
  navy-900: "#1a1a2e"
  surface-page: "#f7f8fc"
  surface-card: "#ffffff"
  surface-sidebar: "#1a1a2e"
  text-primary: "#1a1a2e"
  text-secondary: "#333333"
  text-muted: "#888888"
  text-on-brand: "#ffffff"
  status-success: "#2dd36f"
  status-warning: "#ffa600"
  status-danger: "#eb445a"
  border-subtle: "#f4f5f8"
  border-medium: "#d9dce5"
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

- **Primary Brand:** `#3880ff` (azul institucional) con énfasis en `#5260ff`.
- **Surfaces:** `#f7f8fc` para fondo general de la aplicación, `#ffffff` para tarjetas elevadas, `#1a1a2e` (navy profundo) para sidebar y paneles de alto contraste.
- **Status Roles:**
  - Success: `#2dd36f` (actas completadas, aprobadas).
  - Warning: `#ffa600` (actas en disputa, pendientes).
  - Danger: `#eb445a` (errores matemáticos, actas anuladas).

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
