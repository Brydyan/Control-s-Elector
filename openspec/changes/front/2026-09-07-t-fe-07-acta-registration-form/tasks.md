# Tasks — T-FE-07: Formulario de Registro de Acta

## Fase A: FormGroup + FormArray Setup

- [ ] **A.1** — Crear `features/observer/acta-form/acta-form.component.ts`.
  - Standalone: true
  - FormGroup: 
    - `candidatos: FormArray` (cada FormControl: {candidatoId, nombre, votos})
    - `votoNulos: FormControl`
    - `votosBlancos: FormControl`
    - `totalSufragantes: FormControl`
  - Validators: required en todos

- [ ] **A.2** — Crear `sumaValidator()` function (custom validator).
  - Recibe FormGroup, valida suma, retorna errors o null

- [ ] **A.3** — Aplicar validator al FormGroup:
  ```typescript
  form = new FormGroup({
    candidatos: new FormArray([...]),
    votoNulos: new FormControl('', Validators.required),
    votosBlancos: new FormControl('', Validators.required),
    totalSufragantes: new FormControl('', Validators.required)
  }, { validators: sumaValidator });
  ```

**Compuerta A:** `ng build`. FormGroup + FormArray creados.

## Fase B: Template y Validación Visual

- [ ] **B.1** — Crear template con:
  - FormArray loop: `*ngFor="let ctrl of candidatos.controls"`
    - Input readonly con nombre candidato
    - Input numérico para votos
  - Inputs para nulos, blancos, sufragantes
  - Display error si form.hasError('sumaNoValida')
  - Botón "Guardar" [disabled]="!form.valid"

- [ ] **B.2** — Test: ingresa valores, suma no cuadra, error debe mostrarse.

**Compuerta B:** Template renderiza, validación visual funciona.

## Fase C: Guardar

- [ ] **C.1** — Agregar método `guardar()`:
  ```typescript
  guardar() {
    if (!form.valid) return;
    loading = true;
    const payload = {
      cargoId: this.cargoId, // vendría de params o selección
      candidatos: form.get('candidatos').value,
      votoNulos: form.get('votoNulos').value,
      votosBlancos: form.get('votosBlancos').value,
      totalSufragantes: form.get('totalSufragantes').value
    };
    this.http.post('/api/observer/actas', payload).subscribe({
      next: () => {
        this.toastr.success('Acta guardada');
        this.router.navigate(['/observer/home']);
      },
      error: (err) => {
        this.errorMessage = 'Error al guardar acta';
        loading = false;
      }
    });
  }
  ```

- [ ] **C.2** — En template, botón "Guardar" llama `(click)="guardar()"`.

**Compuerta C:** POST /api/observer/actas se ejecuta al guardar, navegación ocurre.

## Qué NO hacer

- **No agregar foto** — T-FE-08
- **No guardar borrador** — T-FE-09
- **No traer candidatos desde API** — hardcoded está bien
- **No editar acta existente** — solo crear nueva

## Compuertas

| Fase | Comando | Expected |
|------|---------|----------|
| A | `ng build` | exit 0 |
| B | Cambiar votos | error/success visual |
| C | Click guardar, DevTools Network | POST /api/observer/actas visible |

## Verificación por Mutación

- **V.1** — Comentar sumaValidator → form.valid = true aunque suma no cuadre → restaurar
- **V.2** — Comentar POST en guardar() → click guardar, no navega → restaurar
