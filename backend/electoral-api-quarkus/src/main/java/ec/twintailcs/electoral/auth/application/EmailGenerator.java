package ec.twintailcs.electoral.auth.application;

import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.auth.domain.UsuarioInvalidoException;
import jakarta.enterprise.context.ApplicationScoped;
import java.text.Normalizer;

/**
 * Genera emails automaticos con formato: {3 letras nombre}{3 letras apellido}{3 letras
 * rol}@control.ttcs Ej: Juan Perez, OBSERVER -&gt; juaperobs@control.ttcs
 *
 * <p>Port exacto del {@code EmailGenerator} del backend Spring, para que un usuario creado en
 * cualquiera de las dos implementaciones tenga el mismo email: normaliza tildes, toma las primeras
 * 3 letras de cada parte y, si el email ya existe, agrega un sufijo numerico ({@code juaperobs2@},
 * {@code juaperobs3@}, ...).
 */
@ApplicationScoped
public class EmailGenerator {

  private static final String DOMAIN = "control.ttcs";

  /** Maximo sufijo a intentar (juaperobs1 ... juaperobs100). */
  private static final int MAX_RETRIES = 100;

  /**
   * Genera un email UNICO normalizando nombre, apellido y rol.
   *
   * @param nombre Nombre del usuario
   * @param apellido Apellido del usuario
   * @param rol Rol (ej: MASTER, COORDINATOR, ADMIN, OBSERVER)
   * @return Email generado (ej: juaperobs@control.ttcs)
   * @throws UsuarioInvalidoException si no puede generar un email unico tras MAX_RETRIES intentos
   */
  public String generar(String nombre, String apellido, String rol) {
    String base = generarBase(nombre, apellido, rol);
    if (!existe(base)) {
      return base;
    }

    // Resolver colision con sufijo numerico
    for (int i = 2; i <= MAX_RETRIES; i++) {
      String conSufijo = base.replace("@" + DOMAIN, i + "@" + DOMAIN);
      if (!existe(conSufijo)) {
        return conSufijo;
      }
    }

    throw new UsuarioInvalidoException(
        "No se pudo generar email único para "
            + nombre
            + " "
            + apellido
            + " ("
            + rol
            + ") tras "
            + MAX_RETRIES
            + " intentos");
  }

  /**
   * Genera el email BASE, sin resolver colisiones.
   *
   * <p>Es lo que necesita el seeder del usuario MASTER: el email del admin tiene que ser siempre el
   * mismo ({@code admsismas@control.ttcs}), no uno con sufijo, porque ahi se busca si el
   * administrador ya fue creado.
   */
  public String generarBase(String nombre, String apellido, String rol) {
    return normalizar(nombre, 3) + normalizar(apellido, 3) + normalizar(rol, 3) + "@" + DOMAIN;
  }

  /** Normaliza un string: elimina tildes, extrae las primeras N letras, pasa a minusculas. */
  private String normalizar(String texto, int maxLetras) {
    if (texto == null || texto.isEmpty()) {
      return "";
    }
    String nfd = Normalizer.normalize(texto, Normalizer.Form.NFD);
    String sinMarcas = nfd.replaceAll("\\p{Mn}", "");
    StringBuilder sb = new StringBuilder(maxLetras);
    for (char c : sinMarcas.toLowerCase().toCharArray()) {
      if (Character.isLetter(c)) {
        sb.append(c);
        if (sb.length() >= maxLetras) {
          break;
        }
      }
    }
    return sb.toString();
  }

  private boolean existe(String email) {
    return Usuario.find("email", email).firstResult() != null;
  }
}
