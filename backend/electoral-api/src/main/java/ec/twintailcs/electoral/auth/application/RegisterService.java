package ec.twintailcs.electoral.auth.application;

import ec.twintailcs.electoral.auth.application.usecase.RegistrarUsuarioUseCase;
import ec.twintailcs.electoral.auth.domain.Usuario;
import org.springframework.stereotype.Service;

/** Fachada para registro de usuarios. Delega en RegistrarUsuarioUseCase. */
@Service
public class RegisterService {

  private final RegistrarUsuarioUseCase registrarUsuarioUseCase;

  public RegisterService(RegistrarUsuarioUseCase registrarUsuarioUseCase) {
    this.registrarUsuarioUseCase = registrarUsuarioUseCase;
  }

  public Usuario register(String nombre, String apellido, String passwordPlano, String rolNombre) {
    return registrarUsuarioUseCase.execute(nombre, apellido, passwordPlano, rolNombre);
  }
}
