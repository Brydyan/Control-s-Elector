# Propuesta — Configuración Geográfica Jerárquica

**Fecha:** 2026-11-16  
**Versión:** 1.0  
**Estado:** 📋 Documento de Propuesta

---

## Resumen Ejecutivo

Crear interfaz unificada jerárquica para administración de geografía electoral: **Provincia → Cantón → Parroquia → JRV**. 

MASTER podrá ver relaciones de contexto, crear/editar/eliminar entidades a cada nivel sin salir de la pantalla.

---

## Problema

Actual (Opción A — separada):
- Admin navega entre 4 menús: Provincias, Cantones, Parroquias, JRVs
- Pierde contexto: "¿Cuántas JRVs en Ballenita?" → abrir 3 pantallas, memorizar relaciones
- UX fragmentada

Propuesto (Opción B — jerárquica):
- Una pantalla: `/master/configuracion-geografica`
- Árbol expandible muestra relaciones
- CRUD contextual: "crear parroquia bajo Cantón Santa Elena"
- Contexto visual permanente

---

## Scope

### Dentro de Scope ✅

- Una página `/master/configuracion-geografica`
- Árbol jerárquico expandible/contraíble (Provincia → Cantón → Parroquia → JRV)
- CRUD por nivel: crear/editar/eliminar en cada entidad
- Modales contextuales (crear cantón, crear parroquia, etc.)
- Integración con permisos existentes: recurso `configuracion`, acción `write`
- Validaciones: no eliminar si tiene hijos (ej: no eliminar cantón con parroquias)

### Fuera de Scope ❌

- Importación masiva desde CSV (post-MVP, será SDD separada)
- Replicación de geografía entre ambientes
- Sincronización con CNE en tiempo real
- Cambiar estructura de tablas DB

---

## Users & Goals

| Usuario | Goal |
|---------|------|
| **Master** | Administrar geografía sin SQL: crear cantones, parroquias, JRVs |
| **Admin** | Ver estructura geográfica, lectura (sin CRUD, permisos read-only) |

---

## Requisitos

### Funcionales
1. Expandir/colapsar nodos (Provincia, Cantón, Parroquia)
2. Crear entidad bajo nivel específico (ej: crear Parroquia bajo Cantón seleccionado)
3. Editar entidad (renombrar, cambiar propiedades)
4. Eliminar entidad (con validación: no eliminar si tiene hijos)
5. Ver total de entidades hijas (badge: "2 cantones", "3 parroquias")

### No-Funcionales
- Rendimiento: cargar árbol completo en < 500ms
- Accesibilidad: tree con navegación keyboard completa
- Responsive: colapsar en mobile a vista accordion

---

## Decisiones de Diseño Pendientes

- **D1** — Estructura árbol (recursiva vs PrimeNG Tree vs custom)
- **D2** — Modales (por nivel vs genérico reutilizable)
- **D3** — Validaciones (cascada vs individual)
- **D4** — Persistencia de estado (expandir/colapsar persiste?)

---

## Próximos Pasos

→ Pasar a **design.md** para resolver D1-D4 con alternativas descartadas.
