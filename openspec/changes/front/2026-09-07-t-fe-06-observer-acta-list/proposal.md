# Proposal — T-FE-06: Observer: Lista de Actas Propias

## Qué se construye
Pantalla principal del observer: lista de sus actas registradas (sin pre-asignación). Filtrable por estado (ENVIADA, EN_DISPUTA, CORREGIDA, APROBADA).

## Por qué
- Observer necesita ver qué actas ha registrado.
- Filtro por estado acelera la búsqueda (¿cuáles están en disputa?).
- Base para T-FE-07 (registrar nueva acta).

## Alcance

| Elemento | Incluye | No incluye |
|----------|---------|-----------|
| Componente | HomeComponent en observer feature | paginación |
| API | GET /api/observer/mis-actas | búsqueda por fecha |
| UI | Tabla/lista con columnas: JRV, Cargo, Estado, Fecha | edición inline |
| Filtro | Dropdown por estado | filtro por JRV |
| Navegación | Click en acta → detalles o edición | versioning |

## Criterio de aceptación

- [ ] HomeComponent cargado en /observer/home
- [ ] Llama GET /api/observer/mis-actas al inicializar
- [ ] Muestra lista de actas en tabla/lista
- [ ] Dropdown "Estado" filtra la lista en frontend
- [ ] Click en acta navega a edición/detalles (stubs)
- [ ] Loading spinner mientras se carga

## Fuera de alcance

- Paginación (todas las actas a la vez)
- Búsqueda por JRV
- Exportación (CSV, PDF)
- Edición inline

## Dependencias

- T-FE-02 (rutas)
- T-FE-03 (AuthService)
- T-FE-04 (interceptors)
- T-FE-05 (login para estar autenticado)

## Decisiones a tomar

**D1 — UI component library:** Usar Ionic (IonCard, IonList) o Material? Elegir Ionic (ya en el stack).

**D2 — dónde filtra (frontend vs backend):** Frontend (dropdown en cliente, filtra array).
