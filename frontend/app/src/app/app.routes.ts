import { Routes } from '@angular/router';
import {
  authGuard,
  guestGuard,
  roleGuard,
  sessionGuard,
} from './core/guards/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () =>
      import('./features/auth/pages/login/login.page').then((m) => m.LoginPage),
  },
  {
    // Activación de cuenta. Es pública a propósito: el usuario llega por el link
    // del email y todavía no tiene sesión. `guestGuard` evita que un usuario ya
    // logueado abra una pantalla que no le corresponde.
    path: 'auth/activate',
    canActivate: [guestGuard],
    loadComponent: () =>
      import('./features/auth/pages/activate/activate.page').then(
        (m) => m.ActivatePage,
      ),
  },
  {
    // Alias del enlace de activación (ODD #504, B.5.1). El link canónico que
    // manda el email es /auth/activate; este atajo acepta el /activate?token=…
    // del ODD y redirige conservando los query params.
    path: 'activate',
    redirectTo: 'auth/activate',
    pathMatch: 'full',
  },
  {
    // Resumen de la cuenta activada (ODD #504, B.5.2). Pública a propósito: la
    // persona todavía no tiene sesión, la cuenta recién se activó.
    path: 'auth/account-summary',
    canActivate: [guestGuard],
    loadComponent: () =>
      import('./features/auth/pages/account-summary/account-summary.page').then(
        (m) => m.AccountSummaryPage,
      ),
  },
  {
    // Aceptacion de terminos (T-LEGAL-01.7/.8). Es la pantalla donde vive el
    // modal obligatorio: exige sesion (sin sesion no hay a quien registrar la
    // aceptacion) pero NO pasa por `authGuard`, que es el que manda aqui
    // cuando la version vigente todavia no se acepto.
    path: 'terminos',
    canActivate: [sessionGuard],
    loadComponent: () =>
      import('./features/legal/pages/terms-acceptance/terms-acceptance.page').then(
        (m) => m.TermsAcceptancePage,
      ),
  },
  {
    // Formulario de contacto con el DPO (GDPR Art. 17). Publico a proposito:
    // el derecho a pedir la supresion de la cuenta lo puede ejercer alguien
    // que ya no tiene sesion.
    path: 'contact-dpo',
    loadComponent: () =>
      import('./features/legal/pages/contact-dpo/contact-dpo.page').then(
        (m) => m.ContactDpoPage,
      ),
  },
  {
    // Página de licencias de código abierto (T-COMPLIANCE-03).
    // Pública a propósito: cualquier usuario debe poder acceder al listado
    // de licencias de dependencias y atribuciones de assets.
    path: 'licenses',
    loadComponent: () =>
      import('./features/legal/pages/licenses/licenses.page').then(
        (m) => m.LicensesPage,
      ),
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
        path: 'observer/mis-actas',
        canActivate: [roleGuard],
        loadComponent: () =>
          import('./features/observer/pages/mis-actas/mis-actas.page').then(
            (m) => m.MisActasPage,
          ),
        data: {
          roles: ['OBSERVER', 'MASTER'],
          title: 'Mis Actas',
          description:
            'Historial, trazabilidad y estado de las actas electorales transmitidas.',
          icon: 'document-text-outline',
        },
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
        path: 'coordinator/reportes',
        canActivate: [roleGuard],
        loadComponent: () =>
          import('./features/coordinator/pages/reportes/coordinator-reportes.page').then(
            (m) => m.CoordinatorReportesPage,
          ),
        data: {
          roles: ['COORDINATOR', 'MASTER', 'ADMIN'],
          title: 'Centro de Reportes',
          subtitle:
            'Métricas operativas y generación de expedientes electorales',
          icon: 'pie-chart-outline',
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
        path: 'master/configuracion',
        canActivate: [roleGuard],
        loadComponent: () =>
          import('./features/master/pages/configuracion/master-configuracion.page').then(
            (m) => m.MasterConfiguracionPage,
          ),
        data: {
          roles: ['MASTER'],
          tab: 'cargos',
          title: 'Catálogos Electorales',
          description:
            'Configuración integral de dignidades, partidos políticos y candidatos.',
          icon: 'list-outline',
        },
      },
      {
        path: 'master/cargos',
        redirectTo: 'master/configuracion',
        pathMatch: 'full',
      },
      {
        path: 'master/partidos',
        redirectTo: 'master/configuracion',
        pathMatch: 'full',
      },
      {
        path: 'master/candidatos',
        redirectTo: 'master/configuracion',
        pathMatch: 'full',
      },
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
    ],
  },
  { path: '**', redirectTo: '' },
];
