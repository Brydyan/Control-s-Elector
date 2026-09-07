# Proposal — T-FE-07: Formulario de Registro de Acta

## Qué se construye
Formulario donde observer ingresa votos del acta. El corazón de la app:
- Lista de candidatos con input de votos
- Campos para votos nulos, blancos, total sufragantes
- Validación en tiempo real: suma debe cuadrar
- Envío al backend

## Por qué
- Observer registra actas aquí.
- Validación en tiempo real previene errores.
- Sin esto, no hay data electoral.

## Alcance

| Elemento | Incluye | No incluye |
|----------|---------|-----------|
| Formulario | FormArray de candidatos, nulos, blancos, sufragantes | múltiples cargos en un form |
| Validación | Suma debe cuadrar, candidatos > 0 | custom validators complejos |
| UI | Inputs numéricos, validación visual | autocomplete de candidatos |
| Envío | POST /api/observer/actas | adjuntar foto (T-FE-08) |
| Error | Mostrar qué campo falla | logs detallados |

## Criterio de aceptación

- [ ] FormArray de candidatos (cada uno: id, nombre, votos)
- [ ] Inputs: votos nulos, blancos, sufragantes
- [ ] Validación en tiempo real: SUM(votos) + nulos + blancos === sufragantes
- [ ] Si no cuadra, mensaje de error y botón "Guardar" deshabilitado
- [ ] Si cuadra, puede guardar
- [ ] POST /api/observer/actas con los datos
- [ ] Si éxito, redirige a /observer/home o muestra confirmation

## Fuera de alcance

- Cargar candidatos desde API (hardcoded por ahora)
- Múltiples cargos en un form
- Foto del acta (T-FE-08)
- Guardar borrador offline

## Dependencias

- T-FE-06 (lista de actas para navegar de vuelta)
- T-FE-03 (AuthService para estar autenticado)

## Decisiones a tomar

**D1 — FormArray vs FormControl individual:** FormArray (dinámico).

**D2 — Dónde hace el cálculo de validación:** En FormGroup validator (customValidator).
