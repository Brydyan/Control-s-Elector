# T-FE-06 — Observer: Lista de Actas Propias

**Dificultad:** Principiante-Intermedio
**Tiempo estimado:** 2-3 horas
**Depende de:** T-FE-03, T-FE-05

---

## ¿Qué vas a construir?

La pantalla principal del observer: una lista de sus propias actas registradas (libre selección de JRV, sin pre-asignación). Con filtro por estado (ENVIADA, EN_DISPUTA, CORREGIDA, APROBADA).

---

## Conceptos clave

### HttpClient.get<T>() con tipado
TypeScript puede tipar la respuesta del backend:

```typescript
// Retorna un Observable<Acta[]>
this.http.get<Acta[]>('/api/observer/mis-actas').subscribe(actas => {
  console.log(actas[0].estado);  // TypeScript sabe que existe .estado
});
```

### async pipe en el template
En vez de suscribirte manualmente en TypeScript, puedes usar el pipe `async` en el template:

```typescript
// En el componente
actas$ = this.observerService.getMisActas();  // Observable, no nos suscribimos

// En el template
<div *ngFor="let acta of actas$ | async">
  {{ acta.jrv.numero }} - {{ acta.cargo.nombre }}: {{ acta.estado }}
</div>
```

Angular maneja la suscripción y desuscripción automáticamente.

### IonBadge — estado visual con colores
```html
<ion-badge [color]="getBadgeColor(jrv)">
  {{ jrv.cargosPendientes }} pendientes
</ion-badge>
```

---

## Archivos a crear

```
src/app/core/services/
  observer.service.ts           ← llama al backend

src/app/features/observer/
  home/
    observer-home.page.ts
    observer-home.page.html
    observer-home.page.scss
  cargos-jrv/
    cargos-jrv.page.ts
    cargos-jrv.page.html
```

---

## Pasos

### Paso 1 — ObserverService

```typescript
// src/app/core/services/observer.service.ts
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Jrv, Cargo } from '../../shared/models';
import { environment } from '../../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class ObserverService {

  private readonly apiUrl = environment.apiUrl;

  constructor(private http: HttpClient) {}

  /**
   * Obtiene las actas propias del observer autenticado (RLS: registrada_por = user_id)
   * Parámetro opcional: ?estado=ENVIADA
   */
  getMisActas(estado?: string): Observable<Acta[]> {
    let url = `${this.apiUrl}/observer/mis-actas`;
    if (estado) {
      url += `?estado=${estado}`;
    }
    return this.http.get<Acta[]>(url);
  }

  /**
   * Obtiene cargos aplicables a una JRV (endpoint público, sin restricción)
   */
  getCargosDeJrv(jrvId: number): Observable<Cargo[]> {
    return this.http.get<Cargo[]>(`${this.apiUrl}/jrv/${jrvId}/cargos`);
  }
}
```

### Paso 2 — ObserverHomePage (lista de JRVs)

```typescript
// src/app/features/observer/home/observer-home.page.ts
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import {
  IonContent, IonHeader, IonToolbar, IonTitle,
  IonList, IonItem, IonLabel, IonBadge, IonNote,
  IonRefresher, IonRefresherContent, IonSkeletonText,
  IonButton, IonIcon
} from '@ionic/angular/standalone';
import { Observable } from 'rxjs';
import { ObserverService } from '../../../core/services/observer.service';
import { AuthService } from '../../../core/services/auth.service';
import { Jrv } from '../../../shared/models';

@Component({
  selector: 'app-observer-home',
  standalone: true,
  imports: [
    CommonModule,
    IonContent, IonHeader, IonToolbar, IonTitle,
    IonList, IonItem, IonLabel, IonBadge, IonNote,
    IonRefresher, IonRefresherContent, IonSkeletonText,
    IonButton, IonIcon
  ],
  templateUrl: './observer-home.page.html',
  styleUrls: ['./observer-home.page.scss']
})
export class ObserverHomePage implements OnInit {

  jrvs$!: Observable<Jrv[]>;

  constructor(
    private observerService: ObserverService,
    private authService: AuthService,
    private router: Router
  ) {}

  ngOnInit() {
    this.cargarJrvs();
  }

  cargarActas() {
    // Cada vez que se llama, hace un nuevo request al backend (RLS: solo actas propias)
    this.actas$ = this.observerService.getMisActas();
  }

  // Color del badge según el estado del acta
  getBadgeColor(acta: Acta): string {
    switch (acta.estado) {
      case 'APROBADA': return 'success';      // verde
      case 'ENVIADA': return 'medium';        // gris
      case 'EN_DISPUTA': return 'danger';     // rojo
      case 'CORREGIDA': return 'warning';     // amarillo
      default: return 'medium';
    }
  }

  // Al tocar una acta, navegar a su detalle
  verDetalle(acta: Acta) {
    this.router.navigate(['/observer/acta', acta.id]);
  }

  // Pull-to-refresh
  async handleRefresh(event: any) {
    this.cargarJrvs();
    // Esperar a que el observable complete
    this.jrvs$.subscribe(() => event.target.complete());
  }

  logout() {
    this.authService.logout();
  }
}
```

### Paso 3 — Template HTML de la lista de JRVs

```html
<!-- src/app/features/observer/home/observer-home.page.html -->
<ion-header>
  <ion-toolbar color="primary">
    <ion-title>Mis JRVs</ion-title>
    <ion-button slot="end" fill="clear" color="light" (click)="logout()">
      Salir
    </ion-button>
  </ion-toolbar>
</ion-header>

<ion-content>

  <!-- Pull-to-refresh: el usuario jala hacia abajo para recargar -->
  <ion-refresher slot="fixed" (ionRefresh)="handleRefresh($event)">
    <ion-refresher-content></ion-refresher-content>
  </ion-refresher>

  <!-- La lista de JRVs -->
  <ion-list *ngIf="jrvs$ | async as jrvs; else cargando">

    <!-- Mensaje si no hay JRVs asignadas -->
    <ion-item *ngIf="jrvs.length === 0">
      <ion-label>
        <p>No tienes JRVs asignadas</p>
      </ion-label>
    </ion-item>

    <!-- Una fila por JRV -->
    <ion-item
      *ngFor="let jrv of jrvs"
      button
      detail
      (click)="verCargos(jrv)">

      <ion-label>
        <h2>JRV {{ jrv.numero }}</h2>
        <p>{{ jrv.parroquia }}</p>
      </ion-label>

      <!-- Badge con el conteo de cargos -->
      <ion-badge slot="end" [color]="getBadgeColor(jrv)">
        {{ jrv.cargosPendientes }} pendientes
      </ion-badge>

    </ion-item>
  </ion-list>

  <!-- Skeleton mientras carga (indicador de carga) -->
  <ng-template #cargando>
    <ion-list>
      <ion-item *ngFor="let i of [1,2,3,4,5]">
        <ion-label>
          <ion-skeleton-text [animated]="true" style="width: 60%"></ion-skeleton-text>
          <ion-skeleton-text [animated]="true" style="width: 40%"></ion-skeleton-text>
        </ion-label>
      </ion-item>
    </ion-list>
  </ng-template>

</ion-content>
```

### Paso 4 — Página de cargos de una JRV

```typescript
// src/app/features/observer/cargos-jrv/cargos-jrv.page.ts
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import {
  IonContent, IonHeader, IonToolbar, IonTitle, IonBackButton, IonButtons,
  IonList, IonItem, IonLabel, IonBadge, IonButton
} from '@ionic/angular/standalone';
import { ObserverService } from '../../../core/services/observer.service';
import { Cargo, EstadoActa } from '../../../shared/models';

@Component({
  selector: 'app-cargos-jrv',
  standalone: true,
  imports: [
    CommonModule,
    IonContent, IonHeader, IonToolbar, IonTitle, IonBackButton, IonButtons,
    IonList, IonItem, IonLabel, IonBadge, IonButton
  ],
  template: `
    <ion-header>
      <ion-toolbar color="primary">
        <ion-buttons slot="start">
          <ion-back-button defaultHref="/observer/home"></ion-back-button>
        </ion-buttons>
        <ion-title>JRV {{ jrvId }} — Cargos</ion-title>
      </ion-toolbar>
    </ion-header>

    <ion-content>
      <ion-list>
        <ion-item
          *ngFor="let cargo of cargos"
          [button]="!cargo.registrado || cargo.estadoActa === 'EN_DISPUTA'"
          (click)="accionCargo(cargo)">

          <ion-label>
            <h2>{{ cargo.nombre }}</h2>
            <p>{{ cargo.nivel }}</p>
          </ion-label>

          <ion-badge slot="end" [color]="getColorCargo(cargo)">
            {{ getTextoCargo(cargo) }}
          </ion-badge>

        </ion-item>
      </ion-list>
    </ion-content>
  `
})
export class CargosJrvPage implements OnInit {

  jrvId!: number;
  cargos: Cargo[] = [];

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private observerService: ObserverService
  ) {}

  ngOnInit() {
    this.jrvId = Number(this.route.snapshot.paramMap.get('jrvId'));
    this.observerService.getCargosDeJrv(this.jrvId).subscribe(cargos => {
      this.cargos = cargos;
    });
  }

  getColorCargo(cargo: Cargo): string {
    if (!cargo.registrado) return 'medium';
    if (cargo.estadoActa === 'EN_DISPUTA') return 'warning';
    if (cargo.estadoActa === 'APROBADA') return 'success';
    return 'primary';
  }

  getTextoCargo(cargo: Cargo): string {
    if (!cargo.registrado) return 'Pendiente';
    return cargo.estadoActa ?? 'Registrado';
  }

  accionCargo(cargo: Cargo) {
    if (!cargo.registrado) {
      // Ir a registrar el acta
      this.router.navigate(['/observer/jrv', this.jrvId, 'cargo', cargo.id, 'registrar']);
    } else if (cargo.estadoActa === 'EN_DISPUTA' && cargo.actaId) {
      // Ir a corregir el acta en disputa
      this.router.navigate(['/observer/actas', cargo.actaId, 'corregir']);
    }
  }
}
```

---

## Cómo verificar que funciona

1. Logueate como OBSERVER
2. Debes ver la lista de JRVs disponibles del recinto/cantón para libre selección
3. El badge debe mostrar cuántos cargos tiene pendientes
4. Al tocar una JRV, debe navegar a la lista de cargos
5. Los cargos pendientes deben aparecer en gris, los registrados en color según estado
6. Pull-to-refresh debe recargar la lista

---

## Errores comunes

| Error | Causa | Solución |
|-------|-------|----------|
| Lista vacía | No hay JRVs registradas o configuradas para el recinto/cantón | Verifica catálogo de JRVs en BD (selección libre de JRVs; `observer_jrv_asignacion` deprecada) |
| `async pipe` no renderiza | Observable tiene error | Agrega `catchError` al observable y verifica en consola |
| Skeleton se queda pegado | Observable nunca completa | Verifica que el request HTTP completa (200 o error) |
| `ActivatedRoute` no tiene el param | Ruta no configurada correctamente | Verifica que en `observer.routes.ts` la ruta tiene `:jrvId` |
