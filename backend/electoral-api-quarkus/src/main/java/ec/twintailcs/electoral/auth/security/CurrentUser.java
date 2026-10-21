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
}
