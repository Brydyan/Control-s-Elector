package ec.twintailcs.electoral.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Toda {@code @RateLimited} del codigo apunta a una politica configurada y aplicable.
 *
 * <p>El binding del interceptor se resuelve al compilar; el nombre de la politica, en runtime. Este
 * test recorre las clases compiladas para que un nombre mal escrito o una politica {@code
 * key=custom} en una anotacion falle en CI, no en el primer request de produccion.
 */
@DisplayName("@RateLimited apunta a politicas validas")
class RateLimitedAnnotationsTest {

  private static final String PREFIX = "app.ratelimit.policies.";

  @Test
  @DisplayName("cada @RateLimited nombra una politica existente con key ip o user")
  void anotacionesValidas() throws Exception {
    Properties props = loadProperties();
    List<String> problemas = new ArrayList<>();
    List<String> encontradas = new ArrayList<>();

    for (Method m : annotatedMethods()) {
      RateLimited rl = m.getAnnotation(RateLimited.class);
      String where = m.getDeclaringClass().getSimpleName() + "#" + m.getName();
      String key = props.getProperty(PREFIX + rl.value() + ".key");
      encontradas.add(rl.value());
      if (rl.value().isBlank()) {
        problemas.add(where + ": @RateLimited sin nombre");
      } else if (props.getProperty(PREFIX + rl.value() + ".limit") == null) {
        problemas.add(where + ": politica '" + rl.value() + "' no configurada");
      } else if (!"ip".equals(key) && !"user".equals(key)) {
        problemas.add(where + ": politica '" + rl.value() + "' es key=" + key);
      }
    }

    assertThat(problemas).isEmpty();
    assertThat(encontradas)
        .as("las politicas del ticket estan aplicadas")
        .contains(
            "login-ip",
            "reauth",
            "contact-dpo",
            "observer-jrvs",
            "observer-actas",
            "config-read",
            "admin-usuarios");
  }

  private static Properties loadProperties() throws IOException {
    Properties props = new Properties();
    try (InputStream in =
        RateLimitedAnnotationsTest.class
            .getClassLoader()
            .getResourceAsStream("application.properties")) {
      assertThat(in).as("application.properties en el classpath").isNotNull();
      props.load(in);
    }
    return props;
  }

  private static List<Method> annotatedMethods()
      throws IOException, URISyntaxException, ClassNotFoundException {
    Path root =
        Path.of(RateLimited.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    List<Method> methods = new ArrayList<>();
    try (Stream<Path> files = Files.walk(root)) {
      for (Path file : files.filter(p -> p.toString().endsWith(".class")).toList()) {
        String name =
            root.relativize(file)
                .toString()
                .replace('\\', '.')
                .replace('/', '.')
                .replace(".class", "");
        if (name.contains("$")) {
          continue;
        }
        Class<?> type =
            Class.forName(name, false, RateLimitedAnnotationsTest.class.getClassLoader());
        for (Method m : type.getDeclaredMethods()) {
          if (m.isAnnotationPresent(RateLimited.class)) {
            methods.add(m);
          }
        }
      }
    }
    return methods;
  }
}
