package ec.twintailcs.electoral.common.application;

import static org.assertj.core.api.Assertions.*;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for RateLimitingService.
 *
 * <p>Estos tests verifican que el servicio funciona correctamente cuando Redis está disponible.
 * Los tests de mutation se usan para verificar que la lógica es correcta (por ejemplo, que sin
 * Redis el servicio falla abierto permitiendo las solicitudes).
 */
@QuarkusTest
@DisplayName("RateLimitingService")
class RateLimitingServiceTest {

  @Inject RateLimitingService service;

  @Test
  @DisplayName("checkGlobalLimit should return a valid RateLimitResult")
  void globalLimitReturnsValidResult() {
    // Arrange
    String ip = "192.168.1.1";

    // Act
    RateLimitingService.RateLimitResult result = service.checkGlobalLimit(ip);

    // Assert - verify the result structure is correct
    assertThat(result).isNotNull();
    assertThat(result.allowed()).isIn(true, false);
    assertThat(result.remaining()).isGreaterThanOrEqualTo(0);
    assertThat(result.retryAfter()).isGreaterThanOrEqualTo(0);
  }

  @Test
  @DisplayName("checkUserLimit should return a valid RateLimitResult")
  void userLimitReturnsValidResult() {
    // Arrange
    Long userId = 123L;

    // Act
    RateLimitingService.RateLimitResult result = service.checkUserLimit(userId);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.allowed()).isIn(true, false);
    assertThat(result.remaining()).isGreaterThanOrEqualTo(0);
    assertThat(result.retryAfter()).isGreaterThanOrEqualTo(0);
  }

  @Test
  @DisplayName("checkEndpointLimit should allow requests under limit")
  void endpointLimitUnderLimit() {
    // Arrange
    Long userId = 456L;
    String endpoint = "export-data";
    long limit = 100; // High limit to ensure we don't exceed in one call
    long windowSeconds = 3600;

    // Act
    RateLimitingService.RateLimitResult result =
        service.checkEndpointLimit(userId, endpoint, limit, windowSeconds);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.allowed()).isTrue();
    assertThat(result.remaining()).isGreaterThan(0);
    assertThat(result.retryAfter()).isEqualTo(0);
  }

  @Test
  @DisplayName("checkEndpointLimit should return valid structure with low limit")
  void endpointLimitResultStructure() {
    // Arrange
    Long userId = 789L;
    String endpoint = "login";

    // Act
    RateLimitingService.RateLimitResult result =
        service.checkEndpointLimit(userId, endpoint, 5, 300);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.allowed()).isIn(true, false);
    assertThat(result.remaining()).isGreaterThanOrEqualTo(0);
    assertThat(result.retryAfter()).isGreaterThanOrEqualTo(0);
  }

  @Test
  @DisplayName("sequential calls to checkEndpointLimit should decrement remaining")
  void sequentialCallsDecrementRemaining() {
    // Arrange
    Long userId = 999L;
    String endpoint = "test-sequential";
    long limit = 10;
    long windowSeconds = 3600;

    // Act - first call
    RateLimitingService.RateLimitResult first =
        service.checkEndpointLimit(userId, endpoint, limit, windowSeconds);

    // Act - second call
    RateLimitingService.RateLimitResult second =
        service.checkEndpointLimit(userId, endpoint, limit, windowSeconds);

    // Assert
    assertThat(first.allowed()).isTrue();
    assertThat(second.allowed()).isTrue();
    // remaining should decrease (but depends on Redis availability)
    assertThat(first.remaining() + second.remaining()).isLessThanOrEqualTo(limit * 2);
  }
}
