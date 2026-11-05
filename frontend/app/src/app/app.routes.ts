import { Routes } from '@angular/router';
import { authGuard, guestGuard, roleGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () =>
      import('./features/auth/pages/login/login.page').then((m) => m.LoginPage),
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
          import('./features/dashboard/pages/dashboard/dashboard.page').then(
            (m) => m.DashboardPage,
          ),
        data: {
          title: 'Resumen General',
          subtitle: 'Elecciones Seccionales 2027 · Provincia Santa Elena',
        },
      },
      {
        path: 'observer/mis-jrvs',
        canActivate: [roleGuard],
        loadComponent: () =>
          import('./features/observer/pages/mis-jrvs/mis-jrvs.page').then(
            (m) => m.MisJrvsPage,
          ),
        data: { roles: ['OBSERVER', 'MASTER'], title: 'Mis JRVs Asignadas' },
      },
      {
        path: 'observer/jrv/:jrvId/cargos',
        canActivate: [roleGuard],
        loadComponent: () =>
          import('./features/observer/pages/jrv-cargos/jrv-cargos.page').then(
            (m) => m.JrvCargosPage,
          ),
        data: { roles: ['OBSERVER', 'MASTER'], title: 'Dignidades de JRV' },
      },
      {
        path: 'observer/jrv/:jrvId/cargo/:cargoId/registrar',
        canActivate: [roleGuard],
        loadComponent: () =>
          import('./features/observer/pages/registrar-acta/registrar-acta.page').then(
            (m) => m.RegistrarActaPage,
          ),
        data: { roles: ['OBSERVER', 'MASTER'], title: 'Registrar Acta' },
      },
      {
        path: 'observer/acta/:id/corregir',
        canActivate: [roleGuard],
        loadComponent: () =>
          import('./features/observer/pages/correccion-acta/correccion-acta.page').then(
            (m) => m.CorreccionActaPage,
          ),
        data: {
          roles: ['OBSERVER', 'MASTER'],
          title: 'Corregir Acta Disputada',
        },
      },
      {
        path: 'dashboard/resultados',
        canActivate: [roleGuard],
        loadComponent: () =>
          import('./features/dashboard/pages/resultados-cargo/resultados-cargo.page').then(
            (m) => m.ResultadosCargoPage,
          ),
        data: {
          roles: ['ADMIN', 'MASTER', 'COORDINATOR'],
          title: 'Resultados por Cargo',
          subtitle: 'Análisis Electoral · Votos por Dignidad y Candidato',
        },
      },
      {
        path: 'admin/resultados',
        canActivate: [roleGuard],
        loadComponent: () =>
          import('./features/dashboard/pages/resultados-cargo/resultados-cargo.page').then(
            (m) => m.ResultadosCargoPage,
          ),
        data: {
          roles: ['ADMIN', 'MASTER', 'COORDINATOR'],
          title: 'Resultados por Cargo',
          subtitle: 'Análisis Electoral · Votos por Dignidad y Candidato',
        },
      },
      {
        path: 'dashboard/progreso',
        canActivate: [roleGuard],
        loadComponent: () =>
          import('./features/dashboard/pages/progreso-cantones/progreso-cantones.page').then(
            (m) => m.ProgresoCantonesPage,
          ),
        data: {
          roles: ['ADMIN', 'MASTER', 'COORDINATOR'],
          title: 'Progreso de Escrutinio',
          subtitle: 'Avance Territorial · Cantones y Parroquias',
        },
      },
      {
        path: 'admin/progreso',
        canActivate: [roleGuard],
        loadComponent: () =>
          import('./features/dashboard/pages/progreso-cantones/progreso-cantones.page').then(
            (m) => m.ProgresoCantonesPage,
          ),
        data: {
          roles: ['ADMIN', 'MASTER', 'COORDINATOR'],
          title: 'Progreso de Escrutinio',
          subtitle: 'Avance Territorial · Cantones y Parroquias',
        },
      },
      {
        path: 'observer/registrar-acta',
        redirectTo: '/observer/mis-jrvs',
        pathMatch: 'full',
      },
      {
        path: 'actas',
        canActivate: [roleGuard],
        loadComponent: () =>
          import('./features/placeholder/placeholder.page').then(
            (m) => m.PlaceholderPage,
          ),
        data: {
          roles: ['COORDINATOR', 'MASTER', 'ADMIN'],
          title: 'Gestión de Actas',
          description: 'Módulo de consulta y supervisión de actas electorales.',
          icon: 'document-text-outline',
        },
      },
      {
        path: 'coordinator/actas',
        canActivate: [roleGuard],
        loadComponent: () =>
          import('./features/coordinator/pages/actas-lista/actas-lista.page').then(
            (m) => m.ActasListaPage,
          ),
        data: {
          roles: ['COORDINATOR', 'MASTER'],
          title: 'Aprobación de Actas',
          subtitle: 'Supervisión, resolución de inconsistencias y aprobación',
          icon: 'checkmark-circle-outline',
        },
      },
      {
        path: 'coordinator/actas/:id',
        canActivate: [roleGuard],
        loadComponent: () =>
          import('./features/coordinator/pages/acta-detalle/acta-detalle.page').then(
            (m) => m.ActaDetallePage,
          ),
        data: {
          roles: ['COORDINATOR', 'MASTER'],
          title: 'Detalle de Acta',
          subtitle: 'Auditoría de escrutinio, balance y evidencia fotográfica',
          icon: 'document-text-outline',
        },
      },
      {
        path: 'evidencias',
        canActivate: [roleGuard],
        loadComponent: () =>
          import('./features/placeholder/placeholder.page').then(
            (m) => m.PlaceholderPage,
          ),
        data: {
          roles: ['OBSERVER', 'COORDINATOR', 'MASTER', 'ADMIN'],
          title: 'Evidencias Fotográficas',
          description: 'Visor y repositorio de capturas de actas y evidencias.',
          icon: 'images-outline',
        },
      },
      {
        path: 'evidencias/subir',
        canActivate: [roleGuard],
        loadComponent: () =>
          import('./features/placeholder/placeholder.page').then(
            (m) => m.PlaceholderPage,
          ),
        data: {
          roles: ['OBSERVER', 'MASTER'],
          title: 'Subir Evidencia',
          description: 'Carga de respaldos fotográficos de actas electorales.',
          icon: 'cloud-upload-outline',
        },
      },
      { path: 'admin', redirectTo: 'admin/usuarios', pathMatch: 'full' },
      {
        path: 'admin/usuarios',
        canActivate: [roleGuard],
        loadComponent: () =>
          import('./features/admin/pages/usuarios/admin-usuarios.page').then(
            (m) => m.AdminUsuariosPage,
          ),
        data: {
          roles: ['MASTER', 'COORDINATOR', 'ADMIN'],
          title: 'Gestión de Usuarios',
          description:
            'Administración de cuentas de observadores, coordinadores y administradores.',
          icon: 'people-outline',
        },
      },
      {
        path: 'admin/sesiones',
        canActivate: [roleGuard],
        loadComponent: () =>
          import('./features/admin/pages/sesiones/admin-sesiones.page').then(
            (m) => m.AdminSesionesPage,
          ),
        data: {
          roles: ['MASTER', 'COORDINATOR', 'ADMIN'],
          title: 'Sesiones Activas',
          description:
            'Supervisión y revocación de sesiones concurrentes del sistema.',
          icon: 'pulse-outline',
        },
      },
      {
        path: 'master/cargos',
        canActivate: [roleGuard],
        loadComponent: () =>
          import('./features/master/pages/configuracion/master-configuracion.page').then(
            (m) => m.MasterConfiguracionPage,
          ),
        data: {
          roles: ['MASTER'],
          tab: 'cargos',
          title: 'Cargos Electorales',
          description:
            'Configuración de dignidades y tipos de cargos electorales.',
          icon: 'ribbon-outline',
        },
      },
      {
        path: 'master/partidos',
        canActivate: [roleGuard],
        loadComponent: () =>
          import('./features/master/pages/configuracion/master-configuracion.page').then(
            (m) => m.MasterConfiguracionPage,
          ),
        data: {
          roles: ['MASTER'],
          tab: 'partidos',
          title: 'Partidos Políticos',
          description:
            'Registro y administración de organizaciones y movimientos políticos.',
          icon: 'flag-outline',
        },
      },
      {
        path: 'master/candidatos',
        canActivate: [roleGuard],
        loadComponent: () =>
          import('./features/master/pages/configuracion/master-configuracion.page').then(
            (m) => m.MasterConfiguracionPage,
          ),
        data: {
          roles: ['MASTER'],
          tab: 'candidatos',
          title: 'Candidatos',
          description:
            'Padrón de candidatos postulados por dignidad y lista política.',
          icon: 'person-outline',
        },
      },
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
    ],
  },
  { path: '**', redirectTo: '' },
];
