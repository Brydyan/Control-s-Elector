# Proposal — T-FE-16: Módulo de Configuración Preelectoral MASTER (Cargos, Partidos, Candidatos)

## Qué se construye
Módulo de administración preelectoral exclusivo para el rol `MASTER`, reemplazando las páginas `PlaceholderPage` actuales y haciendo operativo el diseño de los mockups [`MASTER/desktop/06-0-configuracion-electoral.html`](file:///home/andy/Escritorio/PROYECTOS/CONTROLs/Control-s-Elector/documents/mockups/MASTER/desktop/06-0-configuracion-electoral.html), [`06-1-nuevo-registro.html`](file:///home/andy/Escritorio/PROYECTOS/CONTROLs/Control-s-Elector/documents/mockups/MASTER/desktop/06-1-nuevo-registro.html) y [`06-2-editar.html`](file:///home/andy/Escritorio/PROYECTOS/CONTROLs/Control-s-Elector/documents/mockups/MASTER/desktop/06-2-editar.html):
1. **Vista Unificada con Pestañas (`/master/configuracion`)**:
   - Pestaña `Cargos`: lista de dignidades con nombre, nivel territorial (`PROVINCIAL`, `CANTONAL`, `PARROQUIAL`, `NACIONAL`), orden y estado.
   - Pestaña `Partidos Políticos`: organizaciones políticas con logo, nombre oficial, siglas, número de lista y color representativo.
   - Pestaña `Candidatos`: padrón de postulantes vinculado al cargo y partido político con foto y orden de lista.
   - Tarjetas métricas superiores con totales consolidados (Total Cargos, Partidos Registrados, Candidatos Postulados).
2. **Operaciones CRUD**:
   - Modales interactivos para **Nuevo Registro** y **Editar Registro** según la entidad seleccionada.
   - Confirmación de eliminación lógica/física con diálogo accesible.
3. **Consumo de la API REST Backend**:
   - `GET / POST / PUT / DELETE` en `/api/v1/configuracion/cargos` ([`CargoController`](file:///home/andy/Escritorio/PROYECTOS/CONTROLs/Control-s-Elector/backend/electoral-api/src/main/java/ec/twintailcs/electoral/electoral/web/CargoController.java)).
   - `GET / POST / PUT / DELETE` en `/api/v1/configuracion/partidos` ([`PartidoController`](file:///home/andy/Escritorio/PROYECTOS/CONTROLs/Control-s-Elector/backend/electoral-api/src/main/java/ec/twintailcs/electoral/electoral/web/PartidoController.java)).
   - `GET / POST / PUT / DELETE` en `/api/v1/configuracion/candidatos` ([`CandidatoController`](file:///home/andy/Escritorio/PROYECTOS/CONTROLs/Control-s-Elector/backend/electoral-api/src/main/java/ec/twintailcs/electoral/electoral/web/CandidatoController.java)).

## Por qué
- Actualmente las rutas `/master/cargos`, `/master/partidos` y `/master/candidatos` están mapeadas a [`PlaceholderPage`](file:///home/andy/Escritorio/PROYECTOS/CONTROLs/Control-s-Elector/frontend/app/src/app/features/placeholder/placeholder.page.ts) con mensajes genéricos.
- El backend tiene una cobertura del 100% de controladores y casos de uso con persistencia en PostgreSQL, pero no existe la UI en Angular que permita al administrador configurar la elección.

## Alcance

| Elemento | Incluye | No incluye |
|---|---|---|
| **Vistas** | `MasterConfiguracionPage` con tabs (`cargos`, `partidos`, `candidatos`) | Configuración de usuarios o sesiones (ya cubierto en `admin-usuarios` y `admin-sesiones`) |
| **Componentes** | Tablas de datos, modales de formulario alta/edición, modales de confirmación de baja | Asignación de veedores a recintos (cubierto en módulo de asignación) |
| **Servicios** | `ConfiguracionService` en Angular para cargos, partidos y candidatos | Servicios de escrutinio |
| **Seguridad** | `roleGuard` restringido a rol `MASTER` | Acceso a veedores u observadores |

## Criterios de Aceptación

- [ ] Las rutas `/master/cargos`, `/master/partidos` y `/master/candidatos` abren el módulo de configuración activando la pestaña correspondiente.
- [ ] Permite crear un nuevo cargo, partido o candidato mediante el modal "+ Nuevo Registro", validando campos obligatorios.
- [ ] Permite editar un registro existente mediante el modal "Editar", actualizando la tabla sin recargar toda la aplicación.
- [ ] Permite eliminar un registro tras confirmación del usuario.
- [ ] Usuarios sin rol `MASTER` son bloqueados por el guard con redirección al dashboard.
