package ec.twintailcs.electoral.security;

/**
 * Roles RBAC del sistema de Control Electoral.
 *
 * Conservamos este enum solo para el codigo del PR de RBAC pendiente
 * (ver EvidenciaService). El sistema nuevo de permisos vive como
 * strings en la DB (ver auth.RolPermiso), no como enum.
 *
 * Jerarquia: MASTER > COORDINATOR > ADMIN > OBSERVER.
 */
public enum Rol {
    MASTER,
    COORDINATOR,
    ADMIN,
    OBSERVER
}
