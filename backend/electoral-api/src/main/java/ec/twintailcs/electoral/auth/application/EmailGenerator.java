package ec.twintailcs.electoral.auth.application;

import ec.twintailcs.electoral.auth.domain.UsuarioRepository;
import java.text.Normalizer;
import org.springframework.stereotype.Component;

/**
 * Genera emails automáticos con formato: {3 letras nombre}{3 letras apellido}{3 letras
 * rol}@control.ttcs Ej: Juan Pérez, OBSERVER → juaperobs@control.ttcs
 *
 * <p>Normaliza tildes/acentos (José → jose). Si el email ya existe, agrega sufijo numérico
 * (juaperobs2@, juaperobs3@, etc).
 */
@Component
public class EmailGenerator {

  private final UsuarioRepository usuarioRepository;
  private static final String DOMAIN = "control.ttcs";
  private static final int MAX_RETRIES =
      100; // Máximo sufijo a intentar (juaperobs1 ... juaperobs100)

  public EmailGenerator(UsuarioRepository usuarioRepository) {
    this.usuarioRepository = usuarioRepository;
  }

  /**
   * Genera un email único normalizando nombre, apellido y rol.
   *
   * @param nombre Nombre del usuario
   * @param apellido Apellido del usuario
   * @param rol Rol (ej: MASTER, COORDINATOR, ADMIN, OBSERVER)
   * @return Email generado (ej: juaperobs@control.ttcs)
   * @throws IllegalArgumentException si no puede generar un email único tras MAX_RETRIES intentos
   */
  public String generar(String nombre, String apellido, String rol) {
    String nombreNorm = normalizar(nombre, 3);
    String apellidoNorm = normalizar(apellido, 3);
    String rolNorm = normalizar(rol, 3);

    String base = nombreNorm + apellidoNorm + rolNorm + "@" + DOMAIN;
    if (!usuarioRepository.findByEmail(base).isPresent()) {
      return base;
    }

    // Resolver colisión con sufijo numérico
    for (int i = 2; i <= MAX_RETRIES; i++) {
      String conSufijo = nombreNorm + apellidoNorm + rolNorm + i + "@" + DOMAIN;
      if (!usuarioRepository.findByEmail(conSufijo).isPresent()) {
        return conSufijo;
      }
    }

    throw new IllegalArgumentException(
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
   * Normaliza un string: elimina tildes/acentos, extrae primeras N letras (solo alfabético),
   * minúsculas.
   */
  private String normalizar(String texto, int maxLetras) {
    if (texto == null || texto.isEmpty()) {
      return "";
    }

    // NFD normalize: descompone caracteres acentuados (é → e + acento combinado)
    // Luego strip de marcas diacríticas (remove combining marks)
    String normalizado = Normalizer.normalize(texto, Normalizer.Form.NFD);
    normalizado = normalizado.replaceAll("\\p{Mn}", ""); // \p{Mn} = combining marks

    // Extrae solo letras alfabéticas, minúsculas, hasta maxLetras
    StringBuilder sb = new StringBuilder();
    for (char c : normalizado.toLowerCase().toCharArray()) {
      if (Character.isLetter(c)) {
        sb.append(c);
        if (sb.length() >= maxLetras) {
          break;
        }
      }
    }

    String resultado = sb.toString();
    // Si tiene menos de maxLetras, rellenar con la parte disponible (no rellena; solo toma lo que
    // hay)
    return resultado;
  }
}
