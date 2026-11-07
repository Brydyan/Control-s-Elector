package ec.twintailcs.electoral.auth.application;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Valida la complejidad de la contrasena que elige el usuario al activar su cuenta.
 *
 * <p>Cuatro requisitos (decision D8 del design): 8 caracteres o mas, una mayuscula, un numero y un
 * caracter especial. Son los que el spec fija y los que la checklist del frontend muestra en tiempo
 * real.
 *
 * <p>El servicio no lanza ni decide: devuelve <em>que</em> falta. Quien llama decide que hacer con
 * eso — el endpoint responde 400 enumerando los faltantes, el componente Angular marca cada fila
 * del checklist. Un validador que solo devuelve booleano obliga a duplicar las reglas en el
 * frontend, y las dos copias se divergen.
 */
@ApplicationScoped
public class PasswordValidationService {

  private static final Pattern HAS_UPPERCASE = Pattern.compile("[A-Z]");
  private static final Pattern HAS_NUMBER = Pattern.compile("[0-9]");
  private static final Pattern HAS_SPECIAL = Pattern.compile("[!@#$%^&*]");

  /**
   * Valida una contrasena.
   *
   * @param password contrasena en claro; null se trata como cadena vacia
   * @return resultado con la validez y los identificadores de los requisitos que faltan
   */
  public PasswordValidationResult validate(String password) {
    String valor = password == null ? "" : password;
    List<String> faltantes = new ArrayList<>();

    if (valor.length() < PasswordValidationResult.MIN_LENGTH) {
      faltantes.add(PasswordValidationResult.REQ_LENGTH);
    }
    if (!HAS_UPPERCASE.matcher(valor).find()) {
      faltantes.add(PasswordValidationResult.REQ_UPPERCASE);
    }
    if (!HAS_NUMBER.matcher(valor).find()) {
      faltantes.add(PasswordValidationResult.REQ_NUMBER);
    }
    if (!HAS_SPECIAL.matcher(valor).find()) {
      faltantes.add(PasswordValidationResult.REQ_SPECIAL);
    }

    return new PasswordValidationResult(faltantes.isEmpty(), faltantes);
  }

  /** True si la contrasena cumple los cuatro requisitos. */
  public boolean esValida(String password) {
    return validate(password).isValid();
  }
}
