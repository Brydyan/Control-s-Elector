# Design — T-FE-07: Formulario de Registro de Acta

## D1 — FormArray vs FormControl Individual

**Decisión:** FormArray para candidatos. Permite agregar/quitar dinámicamente.

**Alternativa descartada — n FormControls nombrados (votoCandidato1, votoCandidato2, ...).** No escalable. FormArray es el patrón.

## D2 — Dónde hace el cálculo de validación

**Decisión:** CustomValidator en FormGroup nivel.
```typescript
function sumaValidator(group: FormGroup): ValidationErrors | null {
  const candidatos = (group.get('candidatos') as FormArray).value;
  const sum = candidatos.reduce((acc, c) => acc + (c.votos || 0), 0);
  const nulos = group.get('votoNulos').value || 0;
  const blancos = group.get('votosBlancos').value || 0;
  const sufragantes = group.get('totalSufragantes').value || 0;
  
  if (sum + nulos + blancos !== sufragantes) {
    return { sumaNoValida: true };
  }
  return null;
}
```

**Alternativa descartada — validación en el template.** Lógica debe estar en el componente/validator, no template.

## D3 — Cargar candidatos: hardcoded o API

**Decisión:** Hardcoded por ahora (candidatos por cargo). T-FE-10 puede traerlos de API.

**Alternativa descartada — GET /api/cargos/{cargoId}/candidatos.** Más tarde si es necesario.

## D4 — Al guardar: redirigir o mostrar success?

**Decisión:** Si éxito, mostrar toast "Acta guardada" y redirigir a /observer/home.

**Alternativa descartada — quedarse en el form.** Feedback más claro al redirigir.
