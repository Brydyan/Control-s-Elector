# T-FE-05 — Pantalla de Login

**Dificultad:** Principiante
**Tiempo estimado:** 2-3 horas
**Depende de:** T-FE-03, T-FE-04

---

## ¿Qué vas a construir?

La pantalla de inicio de sesión. El usuario ingresa email y contraseña, y si las credenciales son correctas, es redirigido automáticamente a su área según su rol.

---

## Conceptos clave

### Formularios Reactivos en Angular
Angular tiene dos sistemas de formularios. Los reactivos son más potentes:

```typescript
// 1. Crear el formulario en el componente
form = new FormGroup({
  email: new FormControl('', [Validators.required, Validators.email]),
  password: new FormControl('', [Validators.required, Validators.minLength(6)])
});

// 2. Conectar en el template
// <form [formGroup]="form">
//   <input formControlName="email">
// </form>

// 3. Validar antes de enviar
if (this.form.valid) {
  const { email, password } = this.form.value;
}
```

### Validators — validación automática
```typescript
Validators.required      // campo no puede estar vacío
Validators.email         // formato de email válido
Validators.minLength(6)  // mínimo 6 caracteres
```

### Componentes Ionic para el diseño
```html
<ion-content>     <!-- contenedor de pantalla -->
<ion-card>        <!-- tarjeta con sombra -->
<ion-input>       <!-- campo de texto estilizado -->
<ion-button>      <!-- botón estilizado -->
<ion-toast>       <!-- mensaje que aparece y desaparece -->
<ion-loading>     <!-- spinner de carga -->
```

---

## Archivos a crear

```
src/app/features/login/
  login.page.ts
  login.page.html
  login.page.scss
```

---

## Pasos

### Paso 1 — Login component (TypeScript)

```typescript
// src/app/features/login/login.page.ts
import { Component, OnInit } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { CommonModule } from '@angular/common';
import {
  IonContent, IonCard, IonCardHeader, IonCardTitle, IonCardContent,
  IonItem, IonLabel, IonInput, IonButton, IonText, IonSpinner,
  ToastController, LoadingController
} from '@ionic/angular/standalone';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    IonContent, IonCard, IonCardHeader, IonCardTitle, IonCardContent,
    IonItem, IonLabel, IonInput, IonButton, IonText, IonSpinner
  ],
  templateUrl: './login.page.html',
  styleUrls: ['./login.page.scss']
})
export class LoginPage {

  // El formulario con sus validaciones
  form = new FormGroup({
    email: new FormControl('', [Validators.required, Validators.email]),
    password: new FormControl('', [Validators.required, Validators.minLength(6)])
  });

  // Estado de carga — true mientras espera respuesta del backend
  cargando = false;

  // URL a la que redirigir después del login (si el usuario intentó acceder a algo)
  private returnUrl = '/';

  constructor(
    private authService: AuthService,
    private router: Router,
    private route: ActivatedRoute,
    private toastCtrl: ToastController,
    private loadingCtrl: LoadingController
  ) {
    // Leer la URL de retorno del query param
    this.returnUrl = this.route.snapshot.queryParams['returnUrl'] || null;
  }

  // Acceso rápido a los campos para verificar errores en el template
  get emailControl() { return this.form.get('email')!; }
  get passwordControl() { return this.form.get('password')!; }

  async onSubmit() {
    // Si el formulario tiene errores, marcar todo como "tocado" para mostrar errores
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const loading = await this.loadingCtrl.create({
      message: 'Iniciando sesión...'
    });
    await loading.present();
    this.cargando = true;

    const { email, password } = this.form.value;

    this.authService.login(email!, password!).subscribe({
      next: async () => {
        await loading.dismiss();
        this.cargando = false;

        // Redirigir a la URL original o a la ruta del rol
        if (this.returnUrl) {
          this.router.navigateByUrl(this.returnUrl);
        } else {
          this.authService.redirigirSegunRol();
        }
      },
      error: async (err) => {
        await loading.dismiss();
        this.cargando = false;

        // Mostrar mensaje de error
        const toast = await this.toastCtrl.create({
          message: err.status === 401
            ? 'Email o contraseña incorrectos'
            : 'Error al conectar con el servidor',
          duration: 3000,
          color: 'danger',
          position: 'top'
        });
        await toast.present();
      }
    });
  }
}
```

### Paso 2 — Template HTML

```html
<!-- src/app/features/login/login.page.html -->
<ion-content class="login-content">

  <div class="login-container">

    <!-- Logo / Título -->
    <div class="login-header">
      <h1>Control Electoral</h1>
      <p>Provincia de Santa Elena</p>
    </div>

    <!-- Tarjeta del formulario -->
    <ion-card class="login-card">
      <ion-card-header>
        <ion-card-title>Iniciar Sesión</ion-card-title>
      </ion-card-header>

      <ion-card-content>
        <form [formGroup]="form" (ngSubmit)="onSubmit()">

          <!-- Campo Email -->
          <ion-item [class.ion-invalid]="emailControl.invalid && emailControl.touched">
            <ion-label position="floating">Email</ion-label>
            <ion-input
              type="email"
              formControlName="email"
              autocomplete="email"
              placeholder="usuario@electoral.ec">
            </ion-input>
          </ion-item>

          <!-- Mensajes de error del email -->
          <div class="error-messages" *ngIf="emailControl.invalid && emailControl.touched">
            <ion-text color="danger">
              <p *ngIf="emailControl.errors?.['required']">El email es obligatorio</p>
              <p *ngIf="emailControl.errors?.['email']">Ingresa un email válido</p>
            </ion-text>
          </div>

          <!-- Campo Password -->
          <ion-item [class.ion-invalid]="passwordControl.invalid && passwordControl.touched">
            <ion-label position="floating">Contraseña</ion-label>
            <ion-input
              type="password"
              formControlName="password"
              autocomplete="current-password">
            </ion-input>
          </ion-item>

          <!-- Mensajes de error del password -->
          <div class="error-messages" *ngIf="passwordControl.invalid && passwordControl.touched">
            <ion-text color="danger">
              <p *ngIf="passwordControl.errors?.['required']">La contraseña es obligatoria</p>
              <p *ngIf="passwordControl.errors?.['minlength']">Mínimo 6 caracteres</p>
            </ion-text>
          </div>

          <!-- Botón de envío -->
          <ion-button
            expand="block"
            type="submit"
            class="login-button"
            [disabled]="cargando">
            <span *ngIf="!cargando">Ingresar</span>
            <ion-spinner *ngIf="cargando" name="crescent"></ion-spinner>
          </ion-button>

        </form>
      </ion-card-content>
    </ion-card>

  </div>

</ion-content>
```

### Paso 3 — Estilos SCSS

```scss
/* src/app/features/login/login.page.scss */

.login-content {
  --background: linear-gradient(135deg, #1a237e 0%, #283593 100%);
}

.login-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 100%;
  padding: 24px;
}

.login-header {
  text-align: center;
  margin-bottom: 32px;
  color: white;

  h1 {
    font-size: 28px;
    font-weight: bold;
    margin: 0;
  }

  p {
    font-size: 14px;
    opacity: 0.8;
    margin: 8px 0 0;
  }
}

.login-card {
  width: 100%;
  max-width: 420px;
  border-radius: 16px;
}

.error-messages {
  padding: 4px 16px 0;

  p {
    font-size: 12px;
    margin: 0;
  }
}

.login-button {
  margin-top: 24px;
}
```

---

## Cómo verificar que funciona

1. Corre `ng serve`
2. Ve a `http://localhost:4200/login`
3. Prueba el formulario vacío → deben aparecer mensajes de error
4. Ingresa un email inválido (sin @) → debe mostrar "Ingresa un email válido"
5. Con credenciales correctas → debe redirigir según el rol
6. Con credenciales incorrectas → debe mostrar toast rojo "Email o contraseña incorrectos"

---

## Errores comunes

| Error | Causa | Solución |
|-------|-------|----------|
| `ReactiveFormsModule` not found | No está en `imports` del componente | Agrega `ReactiveFormsModule` al array `imports` |
| `IonInput` not found | Falta importar el componente Ionic | Importa cada componente Ion que uses en el array `imports` |
| El formulario siempre está inválido | El validador `email` es estricto | Verifica que el email tiene formato correcto: usuario@dominio.com |
| Toast no aparece | `ToastController` no inyectado | Agrega en el constructor |
| Submit se llama aunque hay errores | Falta `if (this.form.invalid) return` | Agrega la verificación al inicio de `onSubmit()` |
