package ec.twintailcs.electoral.security;

/**
 * Roles RBAC del sistema de Control Electoral.
 *
 * Jerarquia: MASTER > COORDINATOR > ADMIN > OBSERVER.
 * Implementar jerarquia real con jerarquia.hasRole() mas adelante;
 * por ahora cada rol tiene permisos independientes.
 */
public enum Rol {
    /** Acceso total. Registra cargos/partidos/candidatos/usuarios. Puede anular actas y revocar sesiones. */
    MASTER,
    /** Operativo global. Gestiona disputas, correcciones y aprobaciones en todas las JRVs. */
    COORDINATOR,
    /** Solo lectura. Dashboard y estadisticas en tiempo real. No modifica datos. */
    ADMIN,
    /** Sube evidencias desde su JRV asignada. */
    OBSERVER
}
