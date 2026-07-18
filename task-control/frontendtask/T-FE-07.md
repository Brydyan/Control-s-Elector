# T-FE-07 — Formulario de Registro de Acta

**Dificultad:** Intermedio-Avanzado
**Tiempo estimado:** 4-5 horas
**Depende de:** T-FE-06

---

## ¿Qué vas a construir?

El formulario donde el observer ingresa los votos del acta. Es el corazón de la app:
- Lista de candidatos con input de votos para cada uno
- Campos para votos nulos, blancos y total sufragantes
- Validación en tiempo real: la suma debe cuadrar
- Envío al backend

---

## Conceptos clave

### FormArray — lista dinámica de controles
Cuando tienes un número variable de campos (uno por candidato), usas `FormArray`:

```typescript
// FormArray de N candidatos
candidatos = new FormArray([
  new FormGroup({ candidatoId: new FormControl(1), votos: new FormControl(0) }),
  new FormGroup({ candidatoId: new FormControl(2), votos: new FormControl(0) }),
]);

// En el template, iteramos con formArrayName + formGroupName
```

### Custom Validator — validación personalizada
Para la regla `SUM(votos) + nulos + blancos === totalSufragantes`:

```typescript
function validarSumaVotos(group: AbstractControl): ValidationErrors | null {
  const total = group.get('totalSufragantes')?.value;
  const nulos = group.get('votosNulos')?.value;
  const blancos = group.get('votosBlancos')?.value;
  const candidatos = group.get('candidatos') as FormArray;
  const sumaVotos = candidatos.controls.reduce((acc, ctrl) => acc + ctrl.get('votos')?.value, 0);

  if (sumaVotos + nulos + blancos !== total) {
    return { sumaInvalida: true };  // retorna el error
  }
  return null;  // sin error
}
```

---

## Archivos a crear

```
src/app/features/observer/
  registrar-acta/
    registrar-acta.page.ts
    registrar-acta.page.html
    registrar-acta.page.scss
```

---

## Código completo

### registrar-acta.page.ts

```typescript
// src/app/features/observer/registrar-acta/registrar-acta.page.ts
import { Component, OnInit } from '@angular/core';
import {
  AbstractControl, FormArray, FormControl, FormGroup,
  ReactiveFormsModule, ValidationErrors, Validators
} from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import {
  IonContent, IonHeader, IonToolbar, IonTitle, IonBackButton, IonButtons,
  IonList, IonItem, IonLabel, IonInput, IonButton, IonText, IonNote,
  ToastController, LoadingController
} from '@ionic/angular/standalone';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';
import { Candidato } from '../../../shared/models';

// ─── Validator personalizado ──────────────────────────────────────────────────
function validarSumaVotos(group: AbstractControl): ValidationErrors | null {
  const totalSufragantes = group.get('totalSufragantes')?.value ?? 0;
  const votosNulos = group.get('votosNulos')?.value ?? 0;
  const votosBlancos = group.get('votosBlancos')?.value ?? 0;
  const candidatosArray = group.get('candidatos') as FormArray;

  const sumaVotos = candidatosArray?.controls.reduce((acc, ctrl) => {
    return acc + (ctrl.get('votos')?.value ?? 0);
  }, 0) ?? 0;

  const total = sumaVotos + votosNulos + votosBlancos;

  if (total !== totalSufragantes && totalSufragantes > 0) {
    return {
      sumaInvalida: true,
      diferencia: totalSufragantes - total,  // cuánto falta o sobra
      calculado: total
    };
  }
  return null;
}

@Component({
  selector: 'app-registrar-acta',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule,
    IonContent, IonHeader, IonToolbar, IonTitle, IonBackButton, IonButtons,
    IonList, IonItem, IonLabel, IonInput, IonButton, IonText, IonNote
  ],
  templateUrl: './registrar-acta.page.html',
  styleUrls: ['./registrar-acta.page.scss']
})
export class RegistrarActaPage implements OnInit {

  jrvId!: number;
  cargoId!: number;
  candidatos: Candidato[] = [];

  // El formulario con el validator personalizado a nivel de grupo
  form = new FormGroup({
    totalSufragantes: new FormControl<number>(0, [Validators.required, Validators.min(1)]),
    votosBlancos: new FormControl<number>(0, [Validators.required, Validators.min(0)]),
    votosNulos: new FormControl<number>(0, [Validators.required, Validators.min(0)]),
    candidatos: new FormArray([])  // se llenará con los candidatos del cargo
  }, { validators: validarSumaVotos });

  // Para mostrar el cálculo en tiempo real
  get sumaActual(): number {
    const nulos = this.form.get('votosNulos')?.value ?? 0;
    const blancos = this.form.get('votosBlancos')?.value ?? 0;
    const votosCandidatos = this.candidatosArray.controls.reduce(
      (acc, ctrl) => acc + (ctrl.get('votos')?.value ?? 0), 0
    );
    return votosCandidatos + nulos + blancos;
  }

  get totalSufragantes(): number {
    return this.form.get('totalSufragantes')?.value ?? 0;
  }

  get candidatosArray(): FormArray {
    return this.form.get('candidatos') as FormArray;
  }

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private http: HttpClient,
    private toastCtrl: ToastController,
    private loadingCtrl: LoadingController
  ) {}

  ngOnInit() {
    this.jrvId = Number(this.route.snapshot.paramMap.get('jrvId'));
    this.cargoId = Number(this.route.snapshot.paramMap.get('cargoId'));
    this.cargarCandidatos();
  }

  cargarCandidatos() {
    // Cargar los candidatos del cargo (endpoint público, sin restricción)
    this.http.get<Candidato[]>(
      `${environment.apiUrl}/cargos/${this.cargoId}/candidatos`
    ).subscribe(candidatos => {
      this.candidatos = candidatos;

      // Crear un FormGroup por candidato en el FormArray
      candidatos.forEach(candidato => {
        this.candidatosArray.push(new FormGroup({
          candidatoId: new FormControl(candidato.id),
          votos: new FormControl(0, [Validators.required, Validators.min(0)])
        }));
      });
    });
  }

  async onSubmit() {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const loading = await this.loadingCtrl.create({ message: 'Registrando acta...' });
    await loading.present();

    const payload = {
      jrvId: this.jrvId,
      cargoId: this.cargoId,
      totalSufragantes: this.form.value.totalSufragantes,
      votosBlancos: this.form.value.votosBlancos,
      votosNulos: this.form.value.votosNulos,
      detalles: this.form.value.candidatos?.map((c: any) => ({
        candidatoId: c.candidatoId,
        votos: c.votos
      }))
    };

    this.http.post(`${environment.apiUrl}/observer/actas`, payload).subscribe({
      next: async () => {
        await loading.dismiss();
        const toast = await this.toastCtrl.create({
          message: 'Acta registrada exitosamente',
          duration: 2000,
          color: 'success'
        });
        await toast.present();
        this.router.navigate(['/observer/home']);
      },
      error: async (err) => {
        await loading.dismiss();
        const mensaje = err.error?.message ?? 'Error al registrar el acta';
        const toast = await this.toastCtrl.create({
          message: mensaje,
          duration: 4000,
          color: 'danger'
        });
        await toast.present();
      }
    });
  }
}
```

### registrar-acta.page.html

```html
<!-- src/app/features/observer/registrar-acta/registrar-acta.page.html -->
<ion-header>
  <ion-toolbar color="primary">
    <ion-buttons slot="start">
      <ion-back-button></ion-back-button>
    </ion-buttons>
    <ion-title>Registrar Acta</ion-title>
  </ion-toolbar>
</ion-header>

<ion-content class="ion-padding" [formGroup]="form">

  <!-- Indicador de suma en tiempo real -->
  <div class="suma-indicador" [class.suma-ok]="sumaActual === totalSufragantes && totalSufragantes > 0">
    <span>Suma actual: {{ sumaActual }}</span>
    <span>Total sufragantes: {{ totalSufragantes }}</span>
  </div>

  <!-- Total Sufragantes -->
  <ion-item>
    <ion-label position="floating">Total Sufragantes *</ion-label>
    <ion-input type="number" formControlName="totalSufragantes" min="1"></ion-input>
  </ion-item>

  <!-- Votos por candidato -->
  <h3 class="seccion-titulo">Votos por Candidato</h3>

  <div formArrayName="candidatos">
    <ion-item *ngFor="let ctrl of candidatosArray.controls; let i = index" [formGroupName]="i">
      <ion-label>{{ candidatos[i]?.nombre }} ({{ candidatos[i]?.partido?.siglas }})</ion-label>
      <ion-input
        slot="end"
        type="number"
        formControlName="votos"
        min="0"
        style="max-width: 80px; text-align: right;">
      </ion-input>
    </ion-item>
  </div>

  <!-- Votos especiales -->
  <h3 class="seccion-titulo">Votos Especiales</h3>

  <ion-item>
    <ion-label position="floating">Votos Blancos</ion-label>
    <ion-input type="number" formControlName="votosBlancos" min="0"></ion-input>
  </ion-item>

  <ion-item>
    <ion-label position="floating">Votos Nulos</ion-label>
    <ion-input type="number" formControlName="votosNulos" min="0"></ion-input>
  </ion-item>

  <!-- Error de validación de suma -->
  <div class="error-suma" *ngIf="form.errors?.['sumaInvalida'] && form.touched">
    <ion-text color="danger">
      <p>
        Los votos no cuadran. Suma actual: {{ sumaActual }} /
        Total sufragantes: {{ totalSufragantes }}
      </p>
    </ion-text>
  </div>

  <!-- Botón de envío -->
  <ion-button
    expand="block"
    class="ion-margin-top"
    (click)="onSubmit()"
    [disabled]="form.invalid">
    Registrar Acta
  </ion-button>

</ion-content>
```

---

## Cómo verificar que funciona

1. Navega a registrar acta para un cargo con candidatos
2. Deja todos los votos en 0 y totalSufragantes en 100 → debe bloquear el botón
3. Ingresa votos que suman: candidato1=60 + candidato2=37 + blancos=2 + nulos=1 = 100
4. El botón se habilita → submite → acta registrada

---

## Errores comunes

| Error | Causa | Solución |
|-------|-------|----------|
| `FormArray` vacío | No se llama `push()` con los candidatos | Verifica que `cargarCandidatos()` hace push al array |
| `formGroupName` error | Usas string en vez de número | El index del `*ngFor` debe ser number |
| Validator nunca corre | Pusiste el validator en el FormControl, no en el FormGroup | El validator de suma va en el `new FormGroup({...}, { validators: ... })` |
| Votos negativos permitidos | Falta `Validators.min(0)` | Agrega en el `FormControl` de votos |
