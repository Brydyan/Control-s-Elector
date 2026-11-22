# T-FE-11 — Dashboard en Tiempo Real (mecanismo por definir)

> **⚠️ HISTÓRICO — ya no describe producción.** Esta tarea se resolvió usando Supabase Realtime.
> El proyecto **no usa Supabase**: el realtime ya no depende de un proveedor administrado y el
> mecanismo (**WebSocket**, en el backend) está **por definir** — ver `documents/diagram/README.md`
> (pendiente "Suscripción WebSocket (mecanismo por definir)"). El contenido de abajo se conserva
> como registro del diseño original con Supabase Realtime.

**Dificultad:** Intermedio-Avanzado
**Tiempo estimado:** 3-4 horas
**Depende de:** T-FE-05

---

## ¿Qué vas a construir?

El dashboard de estadísticas que se actualiza en tiempo real sin recargar la página. Usa Supabase Realtime para recibir notificaciones del servidor cada vez que cambia una acta.

---

## Conceptos clave

### Supabase Realtime — ¿cómo funciona?
Supabase abre un WebSocket entre el navegador y la BD. Cuando se hace un INSERT o UPDATE en la tabla `acta`, Supabase envía un evento al navegador automáticamente.

```
Navegador ←── WebSocket ──→ Supabase ←──── PostgreSQL
                               ↑
                         "acta actualizada!"
```

### @supabase/supabase-js
La librería oficial de Supabase para el cliente:
```bash
npm install @supabase/supabase-js
```

### BehaviorSubject para reactivo
Cuando llega un evento de Supabase, actualizamos un BehaviorSubject. Los componentes se suscriben y se actualizan automáticamente:

```typescript
private actasActualizadas$ = new BehaviorSubject<void>(undefined);

// Cuando llega evento de Supabase:
this.actasActualizadas$.next();

// En el componente, recargar datos cuando cambia:
this.actasActualizadas$.subscribe(() => this.cargarDatos());
```

### async pipe — sin memory leaks
Usar `async pipe` en el template hace que Angular desuscriba automáticamente cuando el componente se destruye. Evita memory leaks.

---

## Archivos a crear

```
src/app/core/services/
  realtime.service.ts
  dashboard.service.ts
src/app/features/dashboard/home/
  dashboard.page.ts
  dashboard.page.html
  dashboard.page.scss
```

---

## Pasos

### Paso 1 — RealtimeService

```typescript
// src/app/core/services/realtime.service.ts
import { Injectable, OnDestroy } from '@angular/core';
import { createClient, SupabaseClient, RealtimeChannel } from '@supabase/supabase-js';
import { Subject } from 'rxjs';
import { environment } from '../../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class RealtimeService implements OnDestroy {

  private supabase: SupabaseClient;
  private channel: RealtimeChannel | null = null;

  // Emite cada vez que una acta cambia
  actaCambiada$ = new Subject<{ tipo: 'INSERT' | 'UPDATE', acta: any }>();

  // Emite cada vez que una notificación llega
  notificacionNueva$ = new Subject<any>();

  constructor() {
    // Crear el cliente de Supabase
    this.supabase = createClient(
      environment.supabaseUrl,
      environment.supabaseAnonKey
    );

    this.conectar();
  }

  private conectar(): void {
    // Suscribirse al canal de cambios en tiempo real
    this.channel = this.supabase
      .channel('electoral-cambios')

      // Escuchar cambios en la tabla 'acta'
      .on(
        'postgres_changes',
        {
          event: '*',        // INSERT, UPDATE, DELETE
          schema: 'public',
          table: 'acta'
        },
        (payload) => {
          console.log('Cambio en acta:', payload);
          this.actaCambiada$.next({
            tipo: payload.eventType as 'INSERT' | 'UPDATE',
            acta: payload.new
          });
        }
      )

      // Escuchar nuevas notificaciones
      .on(
        'postgres_changes',
        {
          event: 'INSERT',
          schema: 'public',
          table: 'notificacion'
        },
        (payload) => {
          this.notificacionNueva$.next(payload.new);
        }
      )

      .subscribe((status) => {
        console.log('Realtime status:', status);
      });
  }

  ngOnDestroy(): void {
    // Limpiar la suscripción cuando el servicio se destruye
    if (this.channel) {
      this.supabase.removeChannel(this.channel);
    }
  }
}
```

### Paso 2 — DashboardService

```typescript
// src/app/core/services/dashboard.service.ts
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface ResumenActas {
  enviadas: number;
  enDisputa: number;
  corregidas: number;
  aprobadas: number;
  totalActivas: number;
}

export interface ResultadoCargo {
  cargoId: number;
  cargoNombre: string;
  candidatoId: number;
  candidatoNombre: string;
  partidoSiglas: string;
  totalVotos: number;
}

export interface ProgresoCantonDto {
  cantonId: number;
  cantonNombre: string;
  totalJrvs: number;
  jrvsConActa: number;
  porcentaje: number;
}

@Injectable({
  providedIn: 'root'
})
export class DashboardService {

  private readonly apiUrl = environment.apiUrl;

  constructor(private http: HttpClient) {}

  getResumen(): Observable<ResumenActas> {
    return this.http.get<ResumenActas>(`${this.apiUrl}/dashboard/resumen`);
  }

  getResultados(): Observable<ResultadoCargo[]> {
    return this.http.get<ResultadoCargo[]>(`${this.apiUrl}/dashboard/resultados`);
  }

  getProgreso(): Observable<ProgresoCantonDto[]> {
    return this.http.get<ProgresoCantonDto[]>(`${this.apiUrl}/dashboard/progreso`);
  }
}
```

### Paso 3 — DashboardPage

```typescript
// src/app/features/dashboard/home/dashboard.page.ts
import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  IonContent, IonHeader, IonToolbar, IonTitle, IonButtons, IonButton,
  IonCard, IonCardHeader, IonCardTitle, IonCardContent,
  IonProgressBar, IonGrid, IonRow, IonCol, IonBadge, IonChip, IonLabel
} from '@ionic/angular/standalone';
import { Subscription } from 'rxjs';
import { DashboardService, ResumenActas, ResultadoCargo, ProgresoCantonDto } from '../../../core/services/dashboard.service';
import { RealtimeService } from '../../../core/services/realtime.service';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    CommonModule,
    IonContent, IonHeader, IonToolbar, IonTitle, IonButtons, IonButton,
    IonCard, IonCardHeader, IonCardTitle, IonCardContent,
    IonProgressBar, IonGrid, IonRow, IonCol, IonBadge, IonChip, IonLabel
  ],
  templateUrl: './dashboard.page.html',
  styleUrls: ['./dashboard.page.scss']
})
export class DashboardPage implements OnInit, OnDestroy {

  resumen: ResumenActas | null = null;
  resultados: ResultadoCargo[] = [];
  progreso: ProgresoCantonDto[] = [];

  // Para agrupar resultados por cargo
  get resultadosPorCargo(): Map<string, ResultadoCargo[]> {
    const mapa = new Map<string, ResultadoCargo[]>();
    this.resultados.forEach(r => {
      const cargo = r.cargoNombre;
      if (!mapa.has(cargo)) mapa.set(cargo, []);
      mapa.get(cargo)!.push(r);
    });
    return mapa;
  }

  // Keys del mapa para usar en ngFor
  get cargos(): string[] {
    return Array.from(this.resultadosPorCargo.keys());
  }

  private subs: Subscription[] = [];

  constructor(
    private dashboardService: DashboardService,
    private realtimeService: RealtimeService,
    private authService: AuthService
  ) {}

  ngOnInit() {
    this.cargarDatos();

    // Cuando una acta cambia (Supabase Realtime), recargar los datos
    const sub = this.realtimeService.actaCambiada$.subscribe(() => {
      console.log('Acta actualizada — recargando dashboard...');
      this.cargarDatos();
    });
    this.subs.push(sub);
  }

  cargarDatos() {
    this.dashboardService.getResumen().subscribe(r => this.resumen = r);
    this.dashboardService.getResultados().subscribe(r => this.resultados = r);
    this.dashboardService.getProgreso().subscribe(r => this.progreso = r);
  }

  // Calcular % de votos de un candidato dentro de su cargo
  calcularPorcentaje(candidato: ResultadoCargo): number {
    const candidatosDelCargo = this.resultadosPorCargo.get(candidato.cargoNombre) ?? [];
    const totalDelCargo = candidatosDelCargo.reduce((acc, c) => acc + c.totalVotos, 0);
    if (totalDelCargo === 0) return 0;
    return Math.round((candidato.totalVotos / totalDelCargo) * 100);
  }

  logout() {
    this.authService.logout();
  }

  ngOnDestroy() {
    this.subs.forEach(s => s.unsubscribe());
  }
}
```

### Paso 4 — Template del dashboard

```html
<!-- dashboard.page.html -->
<ion-header>
  <ion-toolbar color="primary">
    <ion-title>Dashboard Electoral</ion-title>
    <ion-buttons slot="end">
      <ion-button fill="clear" color="light" (click)="logout()">Salir</ion-button>
    </ion-buttons>
  </ion-toolbar>
</ion-header>

<ion-content class="ion-padding">

  <!-- Resumen de actas -->
  <ion-card *ngIf="resumen">
    <ion-card-header>
      <ion-card-title>Resumen de Actas</ion-card-title>
    </ion-card-header>
    <ion-card-content>
      <ion-grid>
        <ion-row>
          <ion-col>
            <div class="stat-box">
              <div class="stat-number primary">{{ resumen.enviadas }}</div>
              <div class="stat-label">Enviadas</div>
            </div>
          </ion-col>
          <ion-col>
            <div class="stat-box">
              <div class="stat-number warning">{{ resumen.enDisputa }}</div>
              <div class="stat-label">En Disputa</div>
            </div>
          </ion-col>
          <ion-col>
            <div class="stat-box">
              <div class="stat-number success">{{ resumen.aprobadas }}</div>
              <div class="stat-label">Aprobadas</div>
            </div>
          </ion-col>
        </ion-row>
      </ion-grid>
    </ion-card-content>
  </ion-card>

  <!-- Progreso por cantón -->
  <ion-card>
    <ion-card-header>
      <ion-card-title>Progreso por Cantón</ion-card-title>
    </ion-card-header>
    <ion-card-content>
      <div *ngFor="let canton of progreso" class="canton-progreso">
        <div class="canton-header">
          <span>{{ canton.cantonNombre }}</span>
          <span>{{ canton.jrvsConActa }}/{{ canton.totalJrvs }} JRVs ({{ canton.porcentaje | number:'1.0-0' }}%)</span>
        </div>
        <ion-progress-bar
          [value]="canton.porcentaje / 100"
          [color]="canton.porcentaje === 100 ? 'success' : 'primary'">
        </ion-progress-bar>
      </div>
    </ion-card-content>
  </ion-card>

  <!-- Resultados por cargo -->
  <ion-card *ngFor="let cargo of cargos">
    <ion-card-header>
      <ion-card-title>{{ cargo }}</ion-card-title>
    </ion-card-header>
    <ion-card-content>
      <div *ngFor="let candidato of resultadosPorCargo.get(cargo)" class="candidato-resultado">
        <div class="candidato-info">
          <span class="candidato-nombre">{{ candidato.candidatoNombre }}</span>
          <ion-chip [style.background]="'#eee'">{{ candidato.partidoSiglas }}</ion-chip>
        </div>
        <div class="candidato-votos">
          <span>{{ candidato.totalVotos | number }} votos ({{ calcularPorcentaje(candidato) }}%)</span>
          <ion-progress-bar
            [value]="calcularPorcentaje(candidato) / 100"
            color="primary">
          </ion-progress-bar>
        </div>
      </div>
    </ion-card-content>
  </ion-card>

</ion-content>
```

---

## Cómo verificar que funciona

1. Abre el dashboard en el navegador
2. En otra pestaña, registra una nueva acta como OBSERVER
3. El dashboard debe actualizarse automáticamente en segundos
4. Verifica en DevTools → Network → WS que hay una conexión WebSocket activa con Supabase

---

## Errores comunes

| Error | Causa | Solución |
|-------|-------|----------|
| WebSocket no conecta | Variables de Supabase incorrectas | Verifica `supabaseUrl` y `supabaseAnonKey` en environment |
| Realtime no llega | Tabla sin replicación habilitada | En Supabase → Database → Replication → habilita `acta` |
| Memory leak | Suscripción no limpiada | Agrega `ngOnDestroy()` con `subs.forEach(s => s.unsubscribe())` |
| `Map` no funciona en ngFor | Angular no itera Maps directamente | Usa `Array.from(mapa.keys())` y accede con `mapa.get(key)` |
