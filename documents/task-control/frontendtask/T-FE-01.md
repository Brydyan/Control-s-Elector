# T-FE-01 — Arquitectura de Carpetas y Módulos Angular (Feature-Based + Core/Shared + Layout)

**Dificultad:** Principiante
**Tiempo estimado:** 2-3 horas
**Depende de:** ninguna

---

## ¿Qué vas a construir?

Estructura de carpetas base del proyecto Angular. Una arquitectura escalable desde el inicio.

**Patrón:** Feature-Based (separación por rol) + Core/Shared (servicios comunes + componentes reutilizables) + Layout (app shell).

---

## Conceptos clave

### Componentes Standalone (Angular 17+)
Sin NgModules. Cada componente declara sus propias dependencias:

```typescript
@Component({
  selector: 'app-ejemplo',
  standalone: true,
  imports: [IonicModule, CommonModule],
  template: `<ion-button>Hola</ion-button>`
})
export class EjemploComponent { }
```

### Feature-Based Organization
Cada rol (observer, coordinator, master, dashboard) es módulo independiente con:
- Componentes propios
- Rutas internas (feature.routes.ts)
- Servicios específicos (si aplica)

### Barrel files (index.ts)
Re-exportar todo de una carpeta para imports limpios:

```typescript
// shared/components/index.ts
export { EstadoBadgeComponent } from './estado-badge/estado-badge.component';
export { ConfirmDialogComponent } from './confirm-dialog/confirm-dialog.component';

// Luego, import limpio:
import { EstadoBadgeComponent, ConfirmDialogComponent } from '../shared/components';
```

### Angular CLI — generar rápido
```bash
ng generate component features/observer/home --standalone
ng generate service core/auth/services/auth
ng generate guard core/auth/guards/auth --implements CanActivate
```

---

## Estructura final

```
frontend/app/src/app/
  
  core/                            ← servicios singleton, guards, interceptors
    auth/                          ← (PARCIALMENTE EXISTE: revisar)
      ├── auth.store.ts            ← signal state: access token en memoria
      ├── auth.interceptor.ts      ← inyectar Bearer + retry automático 401
      ├── data/
      │   └── auth.repository.ts   ← llamadas HTTP /api/auth/*
      ├── guards/
      │   ├── auth.guard.ts        ← autenticado sí/no
      │   └── role.guard.ts        ← rol específico (OBSERVER/COORDINATOR/MASTER/ADMIN)
      ├── services/
      │   └── auth.service.ts      ← orquestar login/logout/refresh
      └── index.ts                 ← barrel file
    
    services/                      ← singletons globales (no auth)
      ├── notification.service.ts  ← ToastController, AlertController
      └── index.ts
  
  layout/                          ← app shell (EXISTE: revisar)
    ├── main-layout/
    │   ├── main-layout.component.ts
    │   ├── main-layout.component.html
    │   └── main-layout.component.scss
    ├── sidebar/
    │   ├── sidebar.component.ts   ← navbar por rol
    │   ├── sidebar.component.html
    │   └── sidebar.component.scss
    ├── topbar/
    │   ├── topbar.component.ts    ← título, subtitle, notificaciones
    │   ├── topbar.component.html
    │   └── topbar.component.scss
    └── index.ts
  
  features/                        ← módulos por rol (Feature-Based)
    ├── login/
    │   ├── login.page.ts
    │   ├── login.page.html
    │   ├── login.page.scss
    │   └── login.routes.ts
    │
    ├── observer/                  ← solo OBSERVER
    │   ├── observer.routes.ts     ← rutas internas observer/*
    │   ├── home/
    │   │   ├── observer-home.page.ts       ← lista actas propias
    │   │   ├── observer-home.page.html
    │   │   └── observer-home.page.scss
    │   ├── registro/
    │   │   ├── registro-acta.page.ts       ← registrar acta nueva
    │   │   ├── registro-acta.page.html
    │   │   └── registro-acta.page.scss
    │   ├── correccion/
    │   │   ├── correccion-acta.page.ts     ← corregir acta EN_DISPUTA
    │   │   ├── correccion-acta.page.html
    │   │   └── correccion-acta.page.scss
    │   ├── captura/
    │   │   ├── captura-foto.page.ts        ← canvas + WebP
    │   │   ├── captura-foto.page.html
    │   │   └── captura-foto.page.scss
    │   └── index.ts
    │
    ├── coordinator/               ← solo COORDINATOR + MASTER
    │   ├── coordinator.routes.ts  ← rutas internas coordinator/*
    │   ├── actas/
    │   │   ├── actas.page.ts      ← listado global + filtros
    │   │   ├── actas.page.html
    │   │   └── actas.page.scss
    │   ├── detalle/
    │   │   ├── acta-detalle.page.ts    ← ver + disputar + aprobar
    │   │   ├── acta-detalle.page.html
    │   │   └── acta-detalle.page.scss
    │   └── index.ts
    │
    ├── master/                    ← solo MASTER
    │   ├── master.routes.ts       ← rutas internas master/*
    │   ├── configuracion/
    │   │   ├── config-electoral.page.ts   ← CRUD cargos/partidos/candidatos
    │   │   ├── config-electoral.page.html
    │   │   └── config-electoral.page.scss
    │   ├── sesiones/
    │   │   ├── sesiones.page.ts   ← ver + revocar sesiones
    │   │   ├── sesiones.page.html
    │   │   └── sesiones.page.scss
    │   └── index.ts
    │
    └── dashboard/                 ← ADMIN + MASTER + COORDINATOR
        ├── dashboard.routes.ts    ← rutas internas dashboard/*
        ├── resumen/
        │   ├── resumen.page.ts    ← totales por estado
        │   ├── resumen.page.html
        │   └── resumen.page.scss
        ├── resultados/
        │   ├── resultados.page.ts ← votos consolidados + gráficos
        │   ├── resultados.page.html
        │   └── resultados.page.scss
        ├── progreso/
        │   ├── progreso.page.ts   ← actas registradas vs total
        │   ├── progreso.page.html
        │   └── progreso.page.scss
        └── index.ts
  
  shared/                          ← componentes reutilizables, models, pipes
    ├── components/
    │   ├── estado-badge/
    │   │   ├── estado-badge.component.ts
    │   │   ├── estado-badge.component.html
    │   │   └── estado-badge.component.scss
    │   ├── rol-badge/
    │   │   ├── rol-badge.component.ts
    │   │   ├── rol-badge.component.html
    │   │   └── rol-badge.component.scss
    │   ├── confirm-dialog/
    │   │   ├── confirm-dialog.component.ts
    │   │   ├── confirm-dialog.component.html
    │   │   └── confirm-dialog.component.scss
    │   ├── notificacion-bell/
    │   │   ├── notificacion-bell.component.ts
    │   │   ├── notificacion-bell.component.html
    │   │   └── notificacion-bell.component.scss
    │   ├── image-capture/
    │   │   ├── image-capture.component.ts
    │   │   ├── image-capture.component.html
    │   │   └── image-capture.component.scss
    │   └── index.ts               ← barrel file
    │
    ├── models/
    │   ├── acta.model.ts
    │   ├── usuario.model.ts
    │   ├── jrv.model.ts
    │   ├── cargo.model.ts
    │   ├── candidato.model.ts
    │   ├── partido.model.ts
    │   ├── estado-acta.enum.ts
    │   └── index.ts               ← barrel file
    │
    ├── pipes/
    │   ├── formato-fecha.pipe.ts
    │   └── index.ts
    │
    └── index.ts                   ← barrel file
  
  app.routes.ts                    ← rutas principales con guards + lazy loading
  app.config.ts                    ← configuración global
  app.component.ts                 ← componente raíz
```

---

## Pasos

### Paso 1 — Revisar lo que ya existe

```bash
# Core/auth (parcialmente existe, revisar):
ls -la frontend/app/src/app/core/auth/

# Layout (existe, revisar):
ls -la frontend/app/src/app/layout/
```

Si falta algún archivo en core/auth o layout, crear solo lo que no esté.

---

### Paso 2 — Crear modelos de datos (shared/models)

```typescript
// src/app/shared/models/estado-acta.enum.ts
export enum EstadoActa {
  BORRADOR = 'BORRADOR',
  ENVIADA = 'ENVIADA',
  EN_DISPUTA = 'EN_DISPUTA',
  CORREGIDA = 'CORREGIDA',
  APROBADA = 'APROBADA',
  ANULADA = 'ANULADA'
}
```

```typescript
// src/app/shared/models/acta.model.ts
import { EstadoActa } from './estado-acta.enum';
import { Jrv, Cargo, Candidato, Partido } from '.';

export interface Acta {
  id: string;
  jrv: Jrv;
  cargo: Cargo;
  registrada_por: string;          ← UUID usuario OBSERVER (NO pre-asignación)
  estado: EstadoActa;
  storageUrl: string | null;
  totalSufragantes: number;
  votosBlancos: number;
  votosNulos: number;
  registrada_en: string;            ← ISO date string
  detalles?: ActaDetalle[];
}

export interface ActaDetalle {
  id: string;
  candidato: Candidato;
  votos: number;
}

export interface Jrv {
  id: string;
  numero: string;
  parroquia: string;
  zona: 'URBANA' | 'RURAL';
}

export interface Cargo {
  id: string;
  nombre: string;
  nivel: 'NACIONAL' | PROVINCIA' | 'CANTON' | 'PARROQUIA';
}

export interface Candidato {
  id: string;
  nombre: string;
  cargo: Cargo;
  partido: Partido;
}

export interface Partido {
  id: string;
  nombre: string;
  siglas: string;
  color: string;
}
```

```typescript
// src/app/shared/models/usuario.model.ts
export type RolUsuario = 'MASTER' | 'COORDINATOR' | 'ADMIN' | 'OBSERVER';

export interface UsuarioAutenticado {
  publicId: string;
  nombre: string;
  email: string;
  rol: RolUsuario;
}

export interface LoginResponse {
  accessToken: string;
  expiresIn: number;
  sesionId: string;
  usuario: UsuarioAutenticado;
}
```

```typescript
// src/app/shared/models/index.ts
export * from './acta.model';
export * from './usuario.model';
export * from './estado-acta.enum';
export * from './jrv.model';
export * from './cargo.model';
export * from './candidato.model';
export * from './partido.model';
```

---

### Paso 3 — Crear carpetas restantes

```bash
# Desde frontend/app/
mkdir -p src/app/features/login
mkdir -p src/app/features/observer/{home,registro,correccion,captura}
mkdir -p src/app/features/coordinator/{actas,detalle}
mkdir -p src/app/features/master/{configuracion,sesiones}
mkdir -p src/app/features/dashboard/{resumen,resultados,progreso}
mkdir -p src/app/shared/components/{estado-badge,rol-badge,confirm-dialog,notificacion-bell,image-capture}
mkdir -p src/app/shared/pipes
mkdir -p src/app/core/services
```

---

### Paso 4 — Crear barrel files

```typescript
// src/app/core/auth/index.ts
export * from './auth.store';
export * from './auth.interceptor';
export * from './guards/auth.guard';
export * from './guards/role.guard';
export * from './services/auth.service';
export * from './data/auth.repository';
```

```typescript
// src/app/core/services/index.ts
export * from './notification.service';
```

```typescript
// src/app/layout/index.ts
export { MainLayoutComponent } from './main-layout/main-layout.component';
export { SidebarComponent } from './sidebar/sidebar.component';
export { TopbarComponent } from './topbar/topbar.component';
```

```typescript
// src/app/features/observer/index.ts
export { OBSERVER_ROUTES } from './observer.routes';
```

```typescript
// src/app/features/coordinator/index.ts
export { COORDINATOR_ROUTES } from './coordinator.routes';
```

```typescript
// src/app/features/master/index.ts
export { MASTER_ROUTES } from './master.routes';
```

```typescript
// src/app/features/dashboard/index.ts
export { DASHBOARD_ROUTES } from './dashboard.routes';
```

```typescript
// src/app/shared/components/index.ts
export { EstadoBadgeComponent } from './estado-badge/estado-badge.component';
export { RolBadgeComponent } from './rol-badge/rol-badge.component';
export { ConfirmDialogComponent } from './confirm-dialog/confirm-dialog.component';
export { NotificacionBellComponent } from './notificacion-bell/notificacion-bell.component';
export { ImageCaptureComponent } from './image-capture/image-capture.component';
```

```typescript
// src/app/shared/index.ts
export * from './components';
export * from './models';
export * from './pipes';
```

---

### Paso 5 — Configurar app.routes.ts

```typescript
// src/app/app.routes.ts
import { Routes } from '@angular/router';
import { AuthGuard, RoleGuard } from './core/auth/guards';
import { LoginComponent } from './features/login/login.component';
import { OBSERVER_ROUTES } from './features/observer';
import { COORDINATOR_ROUTES } from './features/coordinator';
import { MASTER_ROUTES } from './features/master';
import { DASHBOARD_ROUTES } from './features/dashboard';
import { MainLayoutComponent } from './layout';

export const APP_ROUTES: Routes = [
  {
    path: 'login',
    component: LoginComponent
  },
  {
    path: '',
    component: MainLayoutComponent,
    canActivate: [AuthGuard],
    children: [
      {
        path: 'observer',
        canActivate: [RoleGuard(['OBSERVER'])],
        children: OBSERVER_ROUTES
      },
      {
        path: 'coordinator',
        canActivate: [RoleGuard(['COORDINATOR', 'MASTER'])],
        children: COORDINATOR_ROUTES
      },
      {
        path: 'master',
        canActivate: [RoleGuard(['MASTER'])],
        children: MASTER_ROUTES
      },
      {
        path: 'dashboard',
        canActivate: [RoleGuard(['ADMIN', 'MASTER', 'COORDINATOR'])],
        children: DASHBOARD_ROUTES
      }
    ]
  },
  {
    path: '**',
    redirectTo: 'login'
  }
];
```

---

### Paso 6 — Environment variables

```typescript
// src/environments/environment.ts
export const environment = {
  production: false,
  // Ruta relativa: el proxy de dev la manda al backend y deja traveling
  // la cookie httpOnly del refresh token.
  apiUrl: '/api/v1',
  sentryDsn: ''
};
```

```typescript
// src/environments/environment.prod.ts
export const environment = {
  production: true,
  // Fallback: en produccion la URL real se carga en runtime desde
  // assets/config.json (ver CLOUDFLARE_PAGES_SETUP.md).
  apiUrl: 'https://api.brydyan.me/api/v1',
  sentryDsn: ''
};
```

> No hay `supabaseUrl` ni `supabaseAnonKey`: el proyecto **no usa Supabase**. La base es Neon y el
> storage de evidencias es Cloudflare R2, ambos accesados por el backend, no por el navegador.

---

## Cómo verificar que funciona

```bash
cd frontend/app/
ng serve
```

- Debe compilar sin errores
- App abre en `http://localhost:4200`
- Puedes importar modelos: `import { Acta } from '@shared/models'`

---

## Errores comunes

| Error | Causa | Solución |
|-------|-------|----------|
| `Cannot find module '@shared'` | Path alias no configurado | Agrega en `tsconfig.json`: `"@shared": ["src/app/shared"]` |
| `ng generate` falla | No en directorio correcto | Ejecuta desde `frontend/app/` donde está `angular.json` |
| `AuthGuard not found` | Guard no exportado en barrel file | Verifica `core/auth/index.ts` exporta `AuthGuard` |
| Routes circular dependency | features/*.routes.ts importan incorrectamente | Importa routes desde barrel, no directamente |
| Componente no encontrado en route | Ruta mal especificada | Verifica path en feature.routes.ts |

---

## Notas importantes

- **Core/auth existe parcialmente.** Revisar qué ya está antes de crear.
- **Layout existe.** Revisar alineación con MainLayoutComponent.
- **Features son módulos independientes.** Cada uno tiene sus propias rutas internas (observer.routes.ts, etc).
- **Shared es reutilizable entre features.** NO debe importar nada de features.
- **RLS en backend:** OBSERVER solo ve actas con `registrada_por = current_user_id`. Frontend respeta esto vía RoleGuard.
