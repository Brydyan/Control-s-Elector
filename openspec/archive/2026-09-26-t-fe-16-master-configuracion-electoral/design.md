# Design — T-FE-16: Módulo de Configuración Preelectoral MASTER (Cargos, Partidos, Candidatos)

---

## D1 — Estructura de Navegación: Componente Tabulado Único vs Páginas Desconectadas

**Decisión**:
Implementar `MasterConfiguracionPage` con 3 pestañas (`cargos`, `partidos`, `candidatos`). En `app.routes.ts`, las rutas existentes `/master/cargos`, `/master/partidos` y `/master/candidatos` apuntan al mismo componente pasando como `data: { tab: 'cargos' | 'partidos' | 'candidatos' }`.

**Alternativa descartada — Crear tres páginas totalmente aisladas con código duplicado**:
Descartada porque las 3 entidades comparten el mismo layout de cabecera, métricas superiores de totales, modales y estilos visuales. Unificar en un componente tabulado reduce la duplicación de código en un 60% y permite navegación fluida entre pestañas.

---

## D2 — Modales de Creación y Edición Accesibles

**Decisión**:
Implementar componentes modales dedicados (`CargoModalComponent`, `PartidoModalComponent`, `CandidatoModalComponent`) usando `FocusTrapDirective` y validaciones con `ReactiveFormsModule`:
- Validación de unicidad de orden de cargo.
- Validación de número de lista político positivo y siglas en mayúsculas.
- Selector dinámico de partido y cargo al crear o editar un candidato.

**Alternativa descartada — Edición en línea tipo hoja de cálculo en la propia fila**:
Descartada porque no permite incluir selectores complejos con fotos de candidatos ni validaciones asíncronas con facilidad, y rompe la consistencia con el patrón de diseño establecido en los mockups `06-1` y `06-2`.

---

## D3 — Manejo de Dependencias Electorales en Eliminación

**Decisión**:
Si el usuario intenta eliminar un cargo o partido que ya tiene candidatos asignados, el backend responderá con `409 Conflict` o `400 Bad Request` por restricción de clave foránea. El frontend intercepta este error y muestra un mensaje contextual amigable: *"No se puede eliminar: existen candidatos vinculados a este registro"*.

**Alternativa descartada — Eliminación en cascada silenciosa en el cliente**:
Descartada porque borrar candidatos asociados sin advertencia destruiría la configuración de la papeleta electoral.
