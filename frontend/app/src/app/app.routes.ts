import { Routes } from '@angular/router';

import { authGuard, guestGuard, roleGuard } from './core/auth/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () =>
      import('./features/auth/login/login.page').then((m) => m.LoginPage),
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./layout/main-layout/main-layout.component').then(
        (m) => m.MainLayoutComponent,
      ),
    children: [
      {
        path: 'dashboard',
        loadComponent: () =>
          import('./features/dashboard/dashboard.page').then((m) => m.DashboardPage),
        data: {
          title: 'Resumen General',
          subtitle: 'Elecciones Seccionales 2027 · Provincia Santa Elena',
          notifications: 3,
        },
      },
      {
        path: 'observer/mis-jrvs',
        canActivate: [roleGuard],
        loadComponent: () =>
          import('./features/observer/pages/mis-jrvs/mis-jrvs.page').then((m) => m.MisJrvsPage),
        data: {
          roles: ['OBSERVER', 'ADMIN', 'MASTER'],
          title: 'Mis JRVs Asignadas',
        },
      },
      {
        path: 'observer/jrv/:jrvId/cargos',
        canActivate: [roleGuard],
        loadComponent: () =>
          import('./features/observer/pages/jrv-cargos/jrv-cargos.page').then((m) => m.JrvCargosPage),
        data: {
          roles: ['OBSERVER', 'ADMIN', 'MASTER'],
          title: 'Dignidades de JRV',
        },
      },
      {
        path: 'observer/jrv/:jrvId/cargo/:cargoId/registrar',
        canActivate: [roleGuard],
        loadComponent: () =>
          import('./features/observer/pages/registrar-acta/registrar-acta.page').then(
            (m) => m.RegistrarActaPage,
          ),
        data: {
          roles: ['OBSERVER', 'ADMIN', 'MASTER'],
          title: 'Registrar Acta',
        },
      },
      {
        path: '',
        redirectTo: 'dashboard',
        pathMatch: 'full',
      },
    ],
  },
  {
    path: '**',
    redirectTo: '',
  },
];
