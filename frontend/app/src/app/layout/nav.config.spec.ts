import {
  NAV_CONFIG,
  PLACEHOLDER_ROUTES,
  sanitizeNavSections,
  UserRole,
} from './nav.config';

describe('nav.config', () => {
  // ============================================================================
  // A.2.1 — Validar que ningún rol contenga rutas de PLACEHOLDER_ROUTES
  // ============================================================================

  it('should not contain any placeholder routes in NAV_CONFIG', () => {
    const roles: UserRole[] = ['OBSERVER', 'COORDINATOR', 'ADMIN', 'MASTER'];

    for (const role of roles) {
      const sections = NAV_CONFIG[role];
      for (const section of sections) {
        for (const item of section.items) {
          expect(PLACEHOLDER_ROUTES).not.toContain(
            item.route,
            `Role ${role} contains placeholder route: ${item.route}`,
          );
        }
      }
    }
  });

  // ============================================================================
  // A.2.2 — Validar que cada rol reciba exactamente las rutas operativas
  //         asignadas en el SRS sin duplicación interna
  // ============================================================================

  describe('Role OBSERVER', () => {
    it('should have exactly "Operación Electoral" section with Mis JRVs and Mis Actas', () => {
      const sections = NAV_CONFIG['OBSERVER'];
      expect(sections.length).toBe(1);
      expect(sections[0].label).toBe('Operación Electoral');
      expect(sections[0].items.length).toBe(2);
      expect(sections[0].items.map((i) => i.route)).toEqual([
        '/observer/mis-jrvs',
        '/observer/mis-actas',
      ]);
    });

    it('should not have duplicate routes within OBSERVER', () => {
      const sections = NAV_CONFIG['OBSERVER'];
      const routes = sections.flatMap((s) => s.items.map((i) => i.route));
      const uniqueRoutes = new Set(routes);
      expect(routes.length).toBe(uniqueRoutes.size);
    });

    it('should not have duplicate section labels within OBSERVER', () => {
      const sections = NAV_CONFIG['OBSERVER'];
      const labels = sections.map((s) => s.label.toLowerCase());
      const uniqueLabels = new Set(labels);
      expect(labels.length).toBe(uniqueLabels.size);
    });
  });

  describe('Role COORDINATOR', () => {
    it('should have Principal and Actas sections (no Evidencias)', () => {
      const sections = NAV_CONFIG['COORDINATOR'];
      const labels = sections.map((s) => s.label);
      expect(labels).toEqual(['Principal', 'Actas']);
    });

    it('should not have Operación Electoral or Administración sections', () => {
      const sections = NAV_CONFIG['COORDINATOR'];
      const labels = sections.map((s) => s.label.toLowerCase());
      expect(labels).not.toContain('operación electoral');
      expect(labels).not.toContain('administración');
    });

    it('should have Dashboard, Aprobar/Rechazar, and Reportes CNE routes', () => {
      const sections = NAV_CONFIG['COORDINATOR'];
      const routes = sections.flatMap((s) => s.items.map((i) => i.route));
      expect(routes).toEqual([
        '/dashboard',
        '/coordinator/actas',
        '/coordinator/reportes',
      ]);
    });

    it('should not have duplicate routes within COORDINATOR', () => {
      const sections = NAV_CONFIG['COORDINATOR'];
      const routes = sections.flatMap((s) => s.items.map((i) => i.route));
      const uniqueRoutes = new Set(routes);
      expect(routes.length).toBe(uniqueRoutes.size);
    });
  });

  describe('Role ADMIN', () => {
    it('should have only Principal section', () => {
      const sections = NAV_CONFIG['ADMIN'];
      expect(sections.length).toBe(1);
      expect(sections[0].label).toBe('Principal');
    });

    it('should have Dashboard, Resultados, and Progreso Cantonal', () => {
      const sections = NAV_CONFIG['ADMIN'];
      const labels = sections[0].items.map((i) => i.label);
      expect(labels).toEqual(['Dashboard', 'Resultados', 'Progreso Cantonal']);
    });

    it('should not have Actas, Evidencias, or Operación Electoral', () => {
      const sections = NAV_CONFIG['ADMIN'];
      const labels = sections
        .flatMap((s) => s.items.map((i) => i.label))
        .map((l) => l.toLowerCase());
      expect(labels).not.toContain('aprobar / rechazar');
      expect(labels).not.toContain('registrar acta');
      expect(labels).not.toContain('mis jrvs');
    });

    it('should not have duplicate routes within ADMIN', () => {
      const sections = NAV_CONFIG['ADMIN'];
      const routes = sections.flatMap((s) => s.items.map((i) => i.route));
      const uniqueRoutes = new Set(routes);
      expect(routes.length).toBe(uniqueRoutes.size);
    });
  });

  describe('Role MASTER', () => {
    it('should have Principal, Actas, Operación Electoral, Configuración, and Administración', () => {
      const sections = NAV_CONFIG['MASTER'];
      const labels = sections.map((s) => s.label);
      expect(labels).toEqual([
        'Principal',
        'Actas',
        'Operación Electoral',
        'Configuración',
        'Administración',
      ]);
    });

    it('should map Cargos, Partidos, Candidatos to /master/configuracion', () => {
      const sections = NAV_CONFIG['MASTER'];
      const allItems = sections.flatMap((s) => s.items);
      const configItem = allItems.find(
        (i) => i.label === 'Catálogos Electorales',
      );
      expect(configItem?.route).toBe('/master/configuracion');
    });

    it('should have ZERO duplicate routes across all sections', () => {
      const sections = NAV_CONFIG['MASTER'];
      const routes = sections.flatMap((s) => s.items.map((i) => i.route));
      const uniqueRoutes = new Set(routes);
      expect(routes.length).toBe(uniqueRoutes.size);
    });

    it('should have ZERO duplicate section labels', () => {
      const sections = NAV_CONFIG['MASTER'];
      const labels = sections.map((s) => s.label.toLowerCase());
      const uniqueLabels = new Set(labels);
      expect(labels.length).toBe(uniqueLabels.size);
    });

    it('should have all expected routes for MASTER', () => {
      const sections = NAV_CONFIG['MASTER'];
      const routes = sections.flatMap((s) => s.items.map((i) => i.route));
      expect(routes).toContain('/dashboard');
      expect(routes).toContain('/coordinator/actas');
      expect(routes).toContain('/coordinator/reportes');
      expect(routes).toContain('/observer/mis-jrvs');
      expect(routes).toContain('/observer/mis-actas');
      expect(routes).toContain('/master/configuracion');
      expect(routes).toContain('/admin/usuarios');
      expect(routes).toContain('/admin/sesiones');
    });
  });

  // ============================================================================
  // sanitizeNavSections() Tests
  // ============================================================================

  describe('sanitizeNavSections()', () => {
    it('should return canonical config when no backend nodes provided', () => {
      const result = sanitizeNavSections('OBSERVER');
      expect(result).toEqual(NAV_CONFIG['OBSERVER']);
    });

    it('should filter out placeholder routes from sanitized output', () => {
      // Simulate backend returning a placeholder route.
      const backendNodes = [{ label: 'Test', ruta: '/actas', activo: true }];
      const result = sanitizeNavSections('COORDINATOR', backendNodes);

      // Verify no placeholder routes in output.
      const routes = result.flatMap((s) => s.items.map((i) => i.route));
      for (const route of routes) {
        expect(PLACEHOLDER_ROUTES).not.toContain(route);
      }
    });

    it('should maintain role-specific canonical structure', () => {
      const roles: UserRole[] = ['OBSERVER', 'COORDINATOR', 'ADMIN', 'MASTER'];
      for (const role of roles) {
        const result = sanitizeNavSections(role);
        expect(result).toEqual(NAV_CONFIG[role]);
      }
    });
  });

  // ============================================================================
  // Theme Guard Test — Token Contract
  // ============================================================================

  it('should have all routes properly typed', () => {
    const roles: UserRole[] = ['OBSERVER', 'COORDINATOR', 'ADMIN', 'MASTER'];
    for (const role of roles) {
      const sections = NAV_CONFIG[role];
      for (const section of sections) {
        expect(section.label).toBeTruthy();
        expect(Array.isArray(section.items)).toBe(true);
        for (const item of section.items) {
          expect(item.label).toBeTruthy();
          expect(item.route).toBeTruthy();
          expect(item.icon).toBeTruthy();
          expect(item.route).toMatch(/^\/\w+/); // Routes start with /
        }
      }
    }
  });
});
