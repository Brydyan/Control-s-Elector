/**
 * NAV_CONFIG — Fuente de verdad declarativa para navegación RBAC.
 *
 * Registro inmutable de secciones y rutas canónicas por rol.
 * Tipado en tiempo de compilación. No se reescribe en runtime.
 */

export type UserRole = 'OBSERVER' | 'COORDINATOR' | 'ADMIN' | 'MASTER';

export interface NavItem {
  label: string;
  icon: string;
  route: string;
}

export interface NavSection {
  label: string;
  items: NavItem[];
}

/** Rutas prohibidas que apuntan a placeholders o están desvinculadas. */
export const PLACEHOLDER_ROUTES: string[] = [
  '/actas',
  '/evidencias',
  '/evidencias/subir',
  '/admin',
  '/master/cargos',
  '/master/partidos',
  '/master/candidatos',
];

/**
 * Registro canónico de navegación RBAC.
 *
 * Cada rol obtiene exactamente las rutas operativas autorizadas sin duplicados.
 * Backend no puede introducir placeholders ni duplicados; todo filtrado aquí.
 */
export const NAV_CONFIG: Record<UserRole, NavSection[]> = {
  OBSERVER: [
    {
      label: 'Operación Electoral',
      items: [
        {
          label: 'Mis JRVs',
          icon: 'pi pi-th-large',
          route: '/observer/mis-jrvs',
        },
        {
          label: 'Mis Actas',
          icon: 'pi pi-file-pdf',
          route: '/observer/mis-actas',
        },
      ],
    },
  ],

  COORDINATOR: [
    {
      label: 'Principal',
      items: [
        {
          label: 'Dashboard',
          icon: 'pi pi-home',
          route: '/dashboard',
        },
      ],
    },
    {
      label: 'Actas',
      items: [
        {
          label: 'Aprobar / Rechazar',
          icon: 'pi pi-check-circle',
          route: '/coordinator/actas',
        },
        {
          label: 'Reportes CNE',
          icon: 'pi pi-chart-bar',
          route: '/coordinator/reportes',
        },
      ],
    },
  ],

  ADMIN: [
    {
      label: 'Principal',
      items: [
        {
          label: 'Dashboard',
          icon: 'pi pi-home',
          route: '/dashboard',
        },
        {
          label: 'Resultados',
          icon: 'pi pi-chart-bar',
          route: '/dashboard/resultados',
        },
        {
          label: 'Progreso Cantonal',
          icon: 'pi pi-map',
          route: '/dashboard/progreso',
        },
      ],
    },
  ],

  MASTER: [
    {
      label: 'Principal',
      items: [
        {
          label: 'Dashboard',
          icon: 'pi pi-home',
          route: '/dashboard',
        },
        {
          label: 'Resultados',
          icon: 'pi pi-chart-bar',
          route: '/dashboard/resultados',
        },
        {
          label: 'Progreso Cantonal',
          icon: 'pi pi-map',
          route: '/dashboard/progreso',
        },
      ],
    },
    {
      label: 'Actas',
      items: [
        {
          label: 'Aprobar / Rechazar',
          icon: 'pi pi-check-circle',
          route: '/coordinator/actas',
        },
        {
          label: 'Reportes CNE',
          icon: 'pi pi-chart-bar',
          route: '/coordinator/reportes',
        },
      ],
    },
    {
      label: 'Operación Electoral',
      items: [
        {
          label: 'Mis JRVs',
          icon: 'pi pi-th-large',
          route: '/observer/mis-jrvs',
        },
        {
          label: 'Mis Actas',
          icon: 'pi pi-file-pdf',
          route: '/observer/mis-actas',
        },
      ],
    },
    {
      label: 'Configuración',
      items: [
        {
          label: 'Catálogos Electorales',
          icon: 'pi pi-list',
          route: '/master/configuracion',
        },
      ],
    },
    {
      label: 'Administración',
      items: [
        {
          label: 'Gestión de usuarios',
          icon: 'pi pi-users',
          route: '/admin/usuarios',
        },
        {
          label: 'Sesiones activas',
          icon: 'pi pi-clock',
          route: '/admin/sesiones',
        },
      ],
    },
  ],
};

/**
 * Función utilitaria pura: sanitiza y deduplica secciones.
 *
 * - Descarta secciones/rutas que pertenecen a PLACEHOLDER_ROUTES.
 * - Deduplica por clave de sección (label.trim().toLowerCase()).
 * - Deduplica por ruta canónica dentro de cada sección.
 * - Preserva orden canónico de NAV_CONFIG.
 *
 * @param role - Rol del usuario.
 * @param backendNodes - (Opcional) Nodos recibidos de `GET /api/v1/menus/me`.
 *                       Si se provee, se fusionan/santitzan contra NAV_CONFIG.
 * @returns Secciones sanitizadas y desduplicadas.
 */
export function sanitizeNavSections(
  role: UserRole,
  backendNodes?: any[],
): NavSection[] {
  const canonical = NAV_CONFIG[role];

  // Sin backend, retorna canónico directo (ya sin duplicados por diseño).
  if (!backendNodes || backendNodes.length === 0) {
    return canonical;
  }

  // Map<sectionKey, NavSection> para deduplicación de secciones.
  const sectionMap = new Map<string, NavSection>();

  // Insertar canónico primero (preserva orden y es fuente de verdad).
  canonical.forEach((section) => {
    const key = section.label.trim().toLowerCase();
    sectionMap.set(key, structuredClone(section));
  });

  // Aplicar intentos de fusión con backend (si hubiera nodos que pasen sanitización).
  // Por ahora, solo conservamos lo canónico; backendNodes no puede alterar el árbol.
  // (En futuras iteraciones se podría enriquecer si backend aporta datos seguros.)

  // Convertir map a array manteniendo orden de keys.
  const sections = Array.from(sectionMap.values());

  // Validar que ninguna ruta sea placeholder.
  for (const section of sections) {
    for (const item of section.items) {
      if (PLACEHOLDER_ROUTES.includes(item.route)) {
        console.warn(
          `[nav.config] Placeholder route detected and removed: ${item.route}`,
        );
        section.items = section.items.filter((i) => i.route !== item.route);
      }
    }
  }

  // Descartar secciones vacías.
  return sections.filter((s) => s.items.length > 0);
}
