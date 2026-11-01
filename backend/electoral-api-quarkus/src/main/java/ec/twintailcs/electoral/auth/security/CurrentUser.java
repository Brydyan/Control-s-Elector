package ec.twintailcs.electoral.auth.security;

import jakarta.enterprise.context.RequestScoped;
import java.util.List;

/** Usuario autenticado del request en curso. */
@RequestScoped
public class CurrentUser {

  private String email;
  private String rol;
  private List<String> perms = List.of();
  private String sesionId;

  public String email() {
    return email;
  }

  public String rol() {
    return rol;
  }

  public List<String> perms() {
    return perms;
  }

  public String sesionId() {
    return sesionId;
  }

  public void set(String email, String rol, List<String> perms, String sesionId) {
    this.email = email;
    this.rol = rol;
    this.perms = perms;
    this.sesionId = sesionId;
  }

  /**
   * Roles efectivos del usuario: su rol y sus permisos atomicos.
   *
   * <p>Es lo que evalua {@link Requiere}. En Spring, {@code hasRole('MASTER')} busca la authority
   * {@code ROLE_MASTER} y {@code hasAuthority('acta:aprobar')} busca la propia: aca los dos viven
   * en el mismo conjunto, con el rol tal cual y los permisos en su forma {@code recurso:accion}.
   */
  public java.util.Set<String> rolesEfectivos() {
    java.util.Set<String> roles = new java.util.HashSet<>(perms);
    if (rol != null) {
      roles.add(rol);
    }
    return roles;
  }
}
