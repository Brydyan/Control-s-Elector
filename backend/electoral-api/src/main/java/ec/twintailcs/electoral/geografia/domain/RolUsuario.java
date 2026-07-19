package ec.twintailcs.electoral.geografia.domain;

public enum RolUsuario {
    MASTER,       // Administrador total del sistema
    COORDINATOR,  // Coordinador electoral, gestiona disputas y correcciones
    ADMIN,        // Solo lectura, ve dashboards y estadísticas
    OBSERVER      // Registra actas en cualquier JRV (RLS: ve solo sus registros)
}
