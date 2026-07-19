# T-FE-12 — Componentes Compartidos (Shared)

**Dificultad:** Principiante
**Tiempo estimado:** 2-3 horas
**Depende de:** T-FE-01

---

## ¿Qué vas a construir?

Componentes reutilizables que se usan en múltiples pantallas:
- `EstadoBadgeComponent` — chip de color por estado del acta
- `RolBadgeComponent` — chip por rol de usuario
- `ConfirmDialogComponent` — modal genérico de confirmación
- `NotificacionBellComponent` — campana con contador de no leídas

---

## Conceptos clave

### @Input() — recibir datos del componente padre
```typescript
// En el hijo:
@Input() estado!: EstadoActa;

// En el padre (template):
<app-estado-badge [estado]="acta.estado"></app-estado-badge>
```

### @Output() — emitir eventos al padre
```typescript
// En el hijo:
@Output() confirmado = new EventEmitter<string>();

// El hijo emite:
this.confirmado.emit('el motivo');

// El padre escucha:
<app-confirm-dialog (confirmado)="miMetodo($event)"></app-confirm-dialog>
```

### IonModal — modal de Ionic
Para mostrar un modal de confirmación:
```typescript
const modal = await this.modalCtrl.create({
  component: ConfirmDialogComponent,
  componentProps: { titulo: '¿Confirmar?' }
});
await modal.present();
const { data } = await modal.onWillDismiss();
```

---

## Archivos a crear

```
src/app/shared/components/
  estado-badge/
    estado-badge.component.ts
  rol-badge/
    rol-badge.component.ts
  confirm-dialog/
    confirm-dialog.component.ts
  notificacion-bell/
    notificacion-bell.component.ts
  index.ts
```

---

## Código

### EstadoBadgeComponent

```typescript
// src/app/shared/components/estado-badge/estado-badge.component.ts
import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { IonBadge } from '@ionic/angular/standalone';
import { EstadoActa } from '../../models';

@Component({
  selector: 'app-estado-badge',
  standalone: true,
  imports: [CommonModule, IonBadge],
  template: `
    <ion-badge [color]="getColor()">{{ estado }}</ion-badge>
  `
})
export class EstadoBadgeComponent {

  // Recibe el estado del componente padre: <app-estado-badge [estado]="acta.estado">
  @Input() estado!: EstadoActa;

  getColor(): string {
    const colores: Record<EstadoActa, string> = {
      [EstadoActa.BORRADOR]: 'medium',
      [EstadoActa.ENVIADA]: 'primary',
      [EstadoActa.EN_DISPUTA]: 'warning',
      [EstadoActa.CORREGIDA]: 'tertiary',
      [EstadoActa.APROBADA]: 'success',
      [EstadoActa.ANULADA]: 'danger'
    };
    return colores[this.estado] ?? 'medium';
  }
}
```

### RolBadgeComponent

```typescript
// src/app/shared/components/rol-badge/rol-badge.component.ts
import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { IonChip, IonLabel } from '@ionic/angular/standalone';
import { RolUsuario } from '../../models';

@Component({
  selector: 'app-rol-badge',
  standalone: true,
  imports: [CommonModule, IonChip, IonLabel],
  template: `
    <ion-chip [color]="getColor()">
      <ion-label>{{ rol }}</ion-label>
    </ion-chip>
  `
})
export class RolBadgeComponent {

  @Input() rol!: RolUsuario;

  getColor(): string {
    const colores: Record<RolUsuario, string> = {
      MASTER: 'danger',
      COORDINATOR: 'warning',
      ADMIN: 'tertiary',
      OBSERVER: 'primary'
    };
    return colores[this.rol] ?? 'medium';
  }
}
```

### ConfirmDialogComponent

```typescript
// src/app/shared/components/confirm-dialog/confirm-dialog.component.ts
import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  IonHeader, IonToolbar, IonTitle, IonContent, IonButton, IonButtons,
  IonItem, IonLabel, IonTextarea, ModalController
} from '@ionic/angular/standalone';

@Component({
  selector: 'app-confirm-dialog',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    IonHeader, IonToolbar, IonTitle, IonContent, IonButton, IonButtons,
    IonItem, IonLabel, IonTextarea
  ],
  template: `
    <ion-header>
      <ion-toolbar>
        <ion-title>{{ titulo }}</ion-title>
        <ion-buttons slot="end">
          <ion-button (click)="cancelar()">Cancelar</ion-button>
        </ion-buttons>
      </ion-toolbar>
    </ion-header>

    <ion-content class="ion-padding">
      <p>{{ mensaje }}</p>

      <!-- Campo de motivo opcional -->
      <ion-item *ngIf="pedirMotivo">
        <ion-label position="floating">{{ labelMotivo }}</ion-label>
        <ion-textarea [(ngModel)]="motivo" [rows]="3"></ion-textarea>
      </ion-item>

      <ion-button
        expand="block"
        [color]="colorBoton"
        class="ion-margin-top"
        [disabled]="pedirMotivo && !motivo?.trim()"
        (click)="confirmar()">
        {{ textoBoton }}
      </ion-button>
    </ion-content>
  `
})
export class ConfirmDialogComponent {

  // Datos configurables que pasa el padre
  @Input() titulo = '¿Confirmar?';
  @Input() mensaje = '¿Estás seguro?';
  @Input() textoBoton = 'Confirmar';
  @Input() colorBoton: 'primary' | 'danger' | 'warning' | 'success' = 'primary';
  @Input() pedirMotivo = false;
  @Input() labelMotivo = 'Motivo';

  motivo = '';

  constructor(private modalCtrl: ModalController) {}

  confirmar() {
    // Cierra el modal y devuelve el motivo al padre
    this.modalCtrl.dismiss({ confirmado: true, motivo: this.motivo }, 'confirm');
  }

  cancelar() {
    this.modalCtrl.dismiss({ confirmado: false }, 'cancel');
  }
}
```

**Cómo usarlo en otro componente:**
```typescript
// En el componente padre:
async mostrarConfirmacion() {
  const modal = await this.modalCtrl.create({
    component: ConfirmDialogComponent,
    componentProps: {
      titulo: 'Anular Acta',
      mensaje: 'Esta acción es irreversible.',
      textoBoton: 'Anular',
      colorBoton: 'danger',
      pedirMotivo: true,
      labelMotivo: 'Motivo de anulación'
    }
  });
  await modal.present();

  const { data, role } = await modal.onWillDismiss();
  if (role === 'confirm' && data.confirmado) {
    console.log('Motivo:', data.motivo);
    // ejecutar la acción
  }
}
```

### NotificacionBellComponent

```typescript
// src/app/shared/components/notificacion-bell/notificacion-bell.component.ts
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { IonButton, IonIcon, IonBadge } from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import { notificationsOutline } from 'ionicons/icons';
import { environment } from '../../../../environments/environment';
import { RealtimeService } from '../../../core/services/realtime.service';

@Component({
  selector: 'app-notificacion-bell',
  standalone: true,
  imports: [CommonModule, IonButton, IonIcon, IonBadge],
  template: `
    <ion-button fill="clear" color="light" (click)="verNotificaciones()">
      <ion-icon name="notifications-outline"></ion-icon>
      <!-- Badge rojo con el conteo de no leídas -->
      <ion-badge *ngIf="noLeidas > 0" color="danger" style="position: absolute; top: 4px; right: 4px; font-size: 10px;">
        {{ noLeidas > 99 ? '99+' : noLeidas }}
      </ion-badge>
    </ion-button>
  `
})
export class NotificacionBellComponent implements OnInit {

  noLeidas = 0;

  constructor(
    private http: HttpClient,
    private realtimeService: RealtimeService
  ) {
    addIcons({ notificationsOutline });
  }

  ngOnInit() {
    this.cargarConteo();

    // Actualizar cuando llega una nueva notificación en tiempo real
    this.realtimeService.notificacionNueva$.subscribe(() => {
      this.noLeidas++;
    });
  }

  cargarConteo() {
    this.http.get<{ conteo: number }>(
      `${environment.apiUrl}/notificaciones/no-leidas/conteo`
    ).subscribe(res => {
      this.noLeidas = res.conteo;
    });
  }

  verNotificaciones() {
    // Navegar a la pantalla de notificaciones (o abrir un popover)
    console.log('Ver notificaciones...');
    // TODO: abrir panel de notificaciones
  }
}
```

### Barrel file

```typescript
// src/app/shared/components/index.ts
// Exporta todos los componentes para imports limpios

export { EstadoBadgeComponent } from './estado-badge/estado-badge.component';
export { RolBadgeComponent } from './rol-badge/rol-badge.component';
export { ConfirmDialogComponent } from './confirm-dialog/confirm-dialog.component';
export { NotificacionBellComponent } from './notificacion-bell/notificacion-bell.component';
```

---

## Cómo usar los componentes en cualquier pantalla

```typescript
// En el componente que los usa:
import { EstadoBadgeComponent, RolBadgeComponent } from '../../../shared/components';

@Component({
  standalone: true,
  imports: [EstadoBadgeComponent, RolBadgeComponent],  // importar aquí
  template: `
    <app-estado-badge [estado]="acta.estado"></app-estado-badge>
    <app-rol-badge [rol]="usuario.rol"></app-rol-badge>
  `
})
```

---

## Cómo verificar que funciona

1. Importa `EstadoBadgeComponent` en `CoordinatorHomePage`
2. Reemplaza el `<ion-badge>` manual por `<app-estado-badge [estado]="acta.estado">`
3. El badge debe verse igual pero el código es más limpio
4. El color debe cambiar automáticamente según el estado

---

## Errores comunes

| Error | Causa | Solución |
|-------|-------|----------|
| `app-estado-badge` no reconocido | No está importado | Agrega `EstadoBadgeComponent` al array `imports` del componente padre |
| `@Input() estado` es undefined | No pasas el valor en el template | Verifica `[estado]="acta.estado"` (con corchetes) |
| Modal no aparece | `ModalController` no inyectado | Agrega en el constructor del componente padre |
| Badge de notificaciones siempre en 0 | Endpoint de conteo no implementado | Implementa `GET /api/notificaciones/no-leidas/conteo` en el backend |
