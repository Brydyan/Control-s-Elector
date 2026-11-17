package ec.twintailcs.electoral.auth.web.dto;

/**
 * Request de POST /api/user/request-deletion.
 *
 * <p>Solicitud de eliminación de cuenta con confirmación de contraseña.
 */
public class RequestDeletionRequest {

  /** Contraseña en claro para confirmar que es el usuario autenticado. */
  public String password;

  public RequestDeletionRequest() {}

  public RequestDeletionRequest(String password) {
    this.password = password;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }
}
