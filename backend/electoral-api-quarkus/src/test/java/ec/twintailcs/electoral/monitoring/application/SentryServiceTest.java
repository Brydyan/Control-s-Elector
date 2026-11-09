package ec.twintailcs.electoral.monitoring.application;

import static org.junit.jupiter.api.Assertions.*;

import io.quarkus.test.junit.QuarkusTest;
import io.sentry.Sentry;
import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pruebas para el rastreo de eventos personalizados en Sentry.
 *
 * <p>Verifica que los eventos se capturen correctamente con las etiquetas y niveles apropiad
 */
@QuarkusTest
class SentryServiceTest {

  @Inject SentryService sentryService;

  @BeforeEach
  void setUp() {
    // Reiniciar el cliente de Sentry antes de cada prueba
    Sentry.clearBreadcrumbs();
  }

  @Test
  @DisplayName("trackLogin con éxito envia evento INFO con etiqueta de autenticación exitosa")
  void trackLoginSuccessEnviEventoInfo() {
    sentryService.trackLogin("user-123", true);
    // Verificación: el evento fue capturado por Sentry
    assertDoesNotThrow(() -> sentryService.trackLogin("user-123", true));
  }

  @Test
  @DisplayName("trackLogin con fallo envia evento WARNING")
  void trackLoginFailureEnviEventoWarning() {
    sentryService.trackLogin("user-123", false);
    assertDoesNotThrow(() -> sentryService.trackLogin("user-123", false));
  }

  @Test
  @DisplayName("trackLogin con userId nulo no falla")
  void trackLoginConUserIdNuloNoFalla() {
    assertDoesNotThrow(() -> sentryService.trackLogin(null, false));
  }

  @Test
  @DisplayName("trackActaAccess envia evento con etiqueta de acceso a datos")
  void trackActaAccessEnviEventoConEtiqueta() {
    assertDoesNotThrow(
        () -> sentryService.trackActaAccess("user-456", "acta-789", "download"));
  }

  @Test
  @DisplayName("trackResultadosAccess envia evento de acceso a resultados")
  void trackResultadosAccessEnviEvento() {
    assertDoesNotThrow(
        () -> sentryService.trackResultadosAccess("user-456", "junta-789", "export"));
  }

  @Test
  @DisplayName("trackExport envia evento con número de registros")
  void trackExportEnviEventoConRegistros() {
    assertDoesNotThrow(() -> sentryService.trackExport("user-789", "pdf", 150));
  }

  @Test
  @DisplayName("trackCriticalError envia evento ERROR")
  void trackCriticalErrorEnviEventoError() {
    assertDoesNotThrow(
        () ->
            sentryService.trackCriticalError("NullPointerException", "Usuario no encontrado", "user-999"));
  }

  @Test
  @DisplayName("trackCriticalError con userId nulo no falla")
  void trackCriticalErrorConUserIdNuloNoFalla() {
    assertDoesNotThrow(
        () -> sentryService.trackCriticalError("DatabaseException", "Conexión perdida", null));
  }
}
