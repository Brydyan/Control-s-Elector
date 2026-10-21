package ec.twintailcs.electoral.auth.application;

import jakarta.enterprise.context.ApplicationScoped;
import java.text.Normalizer;

/**
 * Genera emails automaticos con formato: {3 letras nombre}{3 letras apellido}{3 letras rol}@control.ttcs
 * Ej: Juan Perez, OBSERVER -> juaperobs@control.ttcs
 *
 * <p>Port exacto del EmailGenerator del backend Spring, para que un usuario creado en cualquiera de
 * las dos implementaciones tenga el mismo email.
 */
@ApplicationScoped
public class EmailGenerator {

  private static final String DOMAIN = "control.ttcs";

  public String generar(String nombre, String apellido, String rol) {
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
}
