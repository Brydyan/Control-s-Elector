# T-FE-10 — Coordinator: Gestión Global de Actas

**Dificultad:** Intermedio
**Tiempo estimado:** 3-4 horas
**Depende de:** T-FE-05

---

## ¿Qué vas a construir?

La pantalla principal del coordinator: una lista filtrable de todas las actas con acciones rápidas para disputar, corregir y aprobar.

---

## Conceptos clave

### IonSearchbar + filtros
Ionic tiene un componente de búsqueda integrado:
```html
<ion-searchbar (ionChange)="filtrar($event)"></ion-searchbar>
```

### IonSelect — filtros dropdown
```html
<ion-select [(ngModel)]="estadoFiltro" (ionChange)="aplicarFiltros()">
  <ion-select-option value="">Todos</ion-select-option>
  <ion-select-option value="ENVIADA">Enviadas</ion-select-option>
</ion-select>
```

### IonAlert — confirmación antes de acciones
Para acciones que requieren confirmación (disputar, aprobar, anular):
```typescript
const alert = await this.alertCtrl.create({
  header: '¿Confirmar?',
  message: 'Esta acción no se puede deshacer',
  inputs: [{ name: 'motivo', type: 'text', placeholder: 'Motivo' }],
  buttons: [
    { text: 'Cancelar', role: 'cancel' },
    { text: 'Confirmar', handler: (data) => this.ejecutarAccion(data.motivo) }
  ]
});
await alert.present();
```

### IonRefresher — pull to refresh
```html
<ion-refresher slot="fixed" (ionRefresh)="recargar($event)">
  <ion-refresher-content></ion-refresher-content>
</ion-refresher>
```

---

## Archivos a crear

```
src/app/core/services/
  coordinator.service.ts
src/app/features/coordinator/home/
  coordinator-home.page.ts
  coordinator-home.page.html
  coordinator-home.page.scss
```

---

## Pasos

### Paso 1 — CoordinatorService

```typescript
// src/app/core/services/coordinator.service.ts
import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Acta, EstadoActa } from '../../shared/models';
import { environment } from '../../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class CoordinatorService {

  private readonly apiUrl = environment.apiUrl;

  constructor(private http: HttpClient) {}

  getActas(estado?: EstadoActa, jrvId?: number, cargoId?: number): Observable<Acta[]> {
    let params = new HttpParams();
    if (estado) params = params.set('estado', estado);
    if (jrvId) params = params.set('jrvId', jrvId.toString());
    if (cargoId) params = params.set('cargoId', cargoId.toString());

    return this.http.get<Acta[]>(`${this.apiUrl}/coordinator/actas`, { params });
  }

  disputar(actaId: number, motivo: string): Observable<Acta> {
    return this.http.put<Acta>(
      `${this.apiUrl}/coordinator/actas/${actaId}/disputar`,
      { motivo }
    );
  }

  aprobar(actaId: number): Observable<Acta> {
    return this.http.put<Acta>(
      `${this.apiUrl}/coordinator/actas/${actaId}/aprobar`,
      {}
    );
  }

  anular(actaId: number, motivo: string): Observable<Acta> {
    return this.http.delete<Acta>(
      `${this.apiUrl}/coordinator/actas/${actaId}`,
      { body: { motivo } }
    );
  }
}
```

### Paso 2 — CoordinatorHomePage

```typescript
// src/app/features/coordinator/home/coordinator-home.page.ts
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  IonContent, IonHeader, IonToolbar, IonTitle,
  IonList, IonItem, IonLabel, IonBadge, IonButton, IonButtons,
  IonRefresher, IonRefresherContent, IonSelect, IonSelectOption,
  IonSearchbar, IonChip, IonIcon,
  AlertController, ToastController, LoadingController
} from '@ionic/angular/standalone';
import { CoordinatorService } from '../../../core/services/coordinator.service';
import { AuthService } from '../../../core/services/auth.service';
import { Acta, EstadoActa } from '../../../shared/models';
import { TokenService } from '../../../core/services/token.service';

@Component({
  selector: 'app-coordinator-home',
  standalone: true,
  imports: [
    CommonModule,
    IonContent, IonHeader, IonToolbar, IonTitle,
    IonList, IonItem, IonLabel, IonBadge, IonButton, IonButtons,
    IonRefresher, IonRefresherContent, IonSelect, IonSelectOption,
    IonSearchbar, IonChip, IonIcon
  ],
  templateUrl: './coordinator-home.page.html',
  styleUrls: ['./coordinator-home.page.scss']
})
export class CoordinatorHomePage implements OnInit {

  actas: Acta[] = [];
  estadoFiltro: EstadoActa | '' = '';
  // Para saber si el usuario es MASTER (puede anular)
  esMaster = false;

  constructor(
    private coordinatorService: CoordinatorService,
    private authService: AuthService,
    private tokenService: TokenService,
    private alertCtrl: AlertController,
    private toastCtrl: ToastController,
    private loadingCtrl: LoadingController
  ) {}

  ngOnInit() {
    this.esMaster = this.tokenService.getRol() === 'MASTER';
    this.cargarActas();
  }

  cargarActas() {
    const estado = this.estadoFiltro as EstadoActa || undefined;
    this.coordinatorService.getActas(estado).subscribe(actas => {
      this.actas = actas;
    });
  }

  getBadgeColor(estado: EstadoActa): string {
    const colores: Record<EstadoActa, string> = {
      BORRADOR: 'medium',
      ENVIADA: 'primary',
      EN_DISPUTA: 'warning',
      CORREGIDA: 'tertiary',
      APROBADA: 'success',
      ANULADA: 'danger'
    };
    return colores[estado] ?? 'medium';
  }

  async accionDisputar(acta: Acta) {
    const alert = await this.alertCtrl.create({
      header: 'Marcar en Disputa',
      message: `Acta JRV ${acta.jrv.numero} — ${acta.cargo.nombre}`,
      inputs: [{
        name: 'motivo',
        type: 'textarea',
        placeholder: 'Describe el problema...'
      }],
      buttons: [
        { text: 'Cancelar', role: 'cancel' },
        {
          text: 'Marcar en Disputa',
          cssClass: 'alert-button-warning',
          handler: (data) => {
            if (!data.motivo?.trim()) return false;  // no cerrar si falta motivo
            this.ejecutarDisputar(acta.id, data.motivo);
            return true;
          }
        }
      ]
    });
    await alert.present();
  }

  private async ejecutarDisputar(actaId: number, motivo: string) {
    const loading = await this.loadingCtrl.create({ message: 'Procesando...' });
    await loading.present();

    this.coordinatorService.disputar(actaId, motivo).subscribe({
      next: async () => {
        await loading.dismiss();
        this.cargarActas();  // refrescar la lista
        const toast = await this.toastCtrl.create({
          message: 'Acta marcada en disputa. El observer será notificado.',
          duration: 3000,
          color: 'warning'
        });
        await toast.present();
      },
      error: async (err) => {
        await loading.dismiss();
        const toast = await this.toastCtrl.create({
          message: err.error?.message ?? 'Error al marcar en disputa',
          duration: 3000,
          color: 'danger'
        });
        await toast.present();
      }
    });
  }

  async accionAprobar(acta: Acta) {
    const alert = await this.alertCtrl.create({
      header: 'Aprobar Acta',
      message: `¿Confirmas la aprobación del acta JRV ${acta.jrv.numero}?`,
      buttons: [
        { text: 'Cancelar', role: 'cancel' },
        {
          text: 'Aprobar',
          cssClass: 'alert-button-success',
          handler: () => {
            this.coordinatorService.aprobar(acta.id).subscribe({
              next: () => this.cargarActas(),
              error: (err) => console.error(err)
            });
          }
        }
      ]
    });
    await alert.present();
  }

  async accionAnular(acta: Acta) {
    // Solo MASTER puede anular
    if (!this.esMaster) return;

    const alert = await this.alertCtrl.create({
      header: 'Anular Acta',
      subHeader: 'Esta acción es IRREVERSIBLE',
      inputs: [{ name: 'motivo', type: 'textarea', placeholder: 'Motivo de anulación (obligatorio)' }],
      buttons: [
        { text: 'Cancelar', role: 'cancel' },
        {
          text: 'Anular',
          cssClass: 'alert-button-danger',
          handler: (data) => {
            if (!data.motivo?.trim()) return false;
            this.coordinatorService.anular(acta.id, data.motivo).subscribe({
              next: () => this.cargarActas()
            });
            return true;
          }
        }
      ]
    });
    await alert.present();
  }

  async handleRefresh(event: any) {
    this.cargarActas();
    setTimeout(() => event.target.complete(), 1000);
  }

  logout() {
    this.authService.logout();
  }
}
```

### Paso 3 — Template

```html
<!-- coordinator-home.page.html -->
<ion-header>
  <ion-toolbar color="primary">
    <ion-title>Gestión de Actas</ion-title>
    <ion-buttons slot="end">
      <ion-button fill="clear" color="light" (click)="logout()">Salir</ion-button>
    </ion-buttons>
  </ion-toolbar>
</ion-header>

<ion-content>
  <ion-refresher slot="fixed" (ionRefresh)="handleRefresh($event)">
    <ion-refresher-content></ion-refresher-content>
  </ion-refresher>

  <!-- Filtro por estado -->
  <ion-item>
    <ion-label>Filtrar por estado</ion-label>
    <ion-select [(ngModel)]="estadoFiltro" (ionChange)="cargarActas()" placeholder="Todos">
      <ion-select-option value="">Todos</ion-select-option>
      <ion-select-option value="ENVIADA">Enviadas</ion-select-option>
      <ion-select-option value="EN_DISPUTA">En Disputa</ion-select-option>
      <ion-select-option value="CORREGIDA">Corregidas</ion-select-option>
      <ion-select-option value="APROBADA">Aprobadas</ion-select-option>
    </ion-select>
  </ion-item>

  <ion-list>
    <ion-item *ngFor="let acta of actas">
      <ion-label>
        <h2>JRV {{ acta.jrv.numero }} — {{ acta.cargo.nombre }}</h2>
        <p>Observer: {{ acta.observer.nombre }}</p>
        <p>{{ acta.fechaRegistro | date:'short' }}</p>
      </ion-label>

      <ion-badge slot="end" [color]="getBadgeColor(acta.estado)">
        {{ acta.estado }}
      </ion-badge>

      <!-- Acciones según el estado del acta -->
      <div slot="end" style="display: flex; gap: 4px;">

        <!-- Disputar: solo si está ENVIADA o CORREGIDA -->
        <ion-button
          *ngIf="acta.estado === 'ENVIADA' || acta.estado === 'CORREGIDA'"
          fill="outline" color="warning" size="small"
          (click)="accionDisputar(acta)">
          Disputar
        </ion-button>

        <!-- Aprobar: solo si está ENVIADA o CORREGIDA -->
        <ion-button
          *ngIf="acta.estado === 'ENVIADA' || acta.estado === 'CORREGIDA'"
          fill="outline" color="success" size="small"
          (click)="accionAprobar(acta)">
          Aprobar
        </ion-button>

        <!-- Anular: solo MASTER, solo actas APROBADAS -->
        <ion-button
          *ngIf="esMaster && acta.estado === 'APROBADA'"
          fill="outline" color="danger" size="small"
          (click)="accionAnular(acta)">
          Anular
        </ion-button>

      </div>
    </ion-item>
  </ion-list>

</ion-content>
```

---

## Cómo verificar que funciona

1. Login como COORDINATOR
2. La lista muestra todas las actas
3. Filtra por estado "ENVIADA" — solo ves las enviadas
4. Toca "Disputar" en una acta → aparece el alert con campo de motivo
5. Escribe el motivo y confirma → el estado cambia a EN_DISPUTA
6. Login como MASTER → el botón "Anular" debe aparecer en actas APROBADAS

---

## Errores comunes

| Error | Causa | Solución |
|-------|-------|----------|
| `[(ngModel)]` error | Falta `FormsModule` en imports | Agrega `FormsModule` al array imports del componente |
| Alert no cierra con handler que retorna false | Bug conocido en Ionic | Usa `alert.dismiss()` manual |
| Botones de acción no aparecen | `acta.estado` no coincide con el string | Asegúrate que el backend envía el estado como string en mayúsculas |
| 403 al anular siendo MASTER | Token no tiene rol MASTER | Verifica el payload del JWT en jwt.io |
