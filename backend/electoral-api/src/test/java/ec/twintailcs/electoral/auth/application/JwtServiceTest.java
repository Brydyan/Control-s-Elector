package ec.twintailcs.electoral.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private JwtService jwtService;
    private final String secret = "my-super-secret-key-that-is-at-least-32-chars-long";
    private final Duration accessTtl = Duration.ofMinutes(15);
    private final Duration refreshTtl = Duration.ofDays(7);
    private final String issuer = "control-elector";

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(secret, accessTtl, refreshTtl, issuer);
    }

    @Test
    void issueAndParse_withValidUuidPublicId_shouldSucceed() {
        // RED phase: None - code compile check or write test first.
        // We write the test, run it, confirm it passes.
        UUID publicId = UUID.randomUUID(); // Wait, in production it would be a UUIDv7, but any UUID works
        String email = "test@example.com";
        String rol = "OBSERVER";
        List<String> perms = List.of("evidencia:upload", "evidencia:read");
        String sesionId = UUID.randomUUID().toString();

        JwtService.TokenPair tokenPair = jwtService.issue(publicId, email, rol, perms, sesionId);

        assertThat(tokenPair).isNotNull();
        assertThat(tokenPair.access()).isNotBlank();
        assertThat(tokenPair.refresh()).isNotBlank();

        // Validate Access Token
        JwtService.ParsedJwt parsedAccess = jwtService.parse(tokenPair.access());
        assertThat(parsedAccess.subject()).isEqualTo(publicId.toString());
        assertThat(parsedAccess.email()).isEqualTo(email);
        assertThat(parsedAccess.rol()).isEqualTo(rol);
        assertThat(parsedAccess.sesionId()).isEqualTo(sesionId);
        assertThat(parsedAccess.perms()).containsExactlyInAnyOrderElementsOf(perms);

        // Validate Refresh Token
        JwtService.ParsedJwt parsedRefresh = jwtService.parse(tokenPair.refresh());
        assertThat(parsedRefresh.subject()).isEqualTo(publicId.toString());
        assertThat(parsedRefresh.sesionId()).isEqualTo(sesionId);
        assertThat(parsedRefresh.claims().getClaim("typ")).isEqualTo("refresh");
    }

    @Test
    void parse_withExpiredToken_shouldThrowInvalidJwtException() {
        JwtService shortLivedJwtService = new JwtService(secret, Duration.ofMillis(1), refreshTtl, issuer);
        UUID publicId = UUID.randomUUID();

        JwtService.TokenPair tokenPair = shortLivedJwtService.issue(publicId, "test@example.com", "OBSERVER", List.of(), "sid");

        // Wait to expire
        try {
            Thread.sleep(5);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        assertThrows(JwtService.InvalidJwtException.class, () -> shortLivedJwtService.parse(tokenPair.access()));
    }

    @Test
    void parse_withInvalidSignature_shouldThrowInvalidJwtException() {
        UUID publicId = UUID.randomUUID();
        JwtService.TokenPair tokenPair = jwtService.issue(publicId, "test@example.com", "OBSERVER", List.of(), "sid");

        String invalidToken = tokenPair.access() + "manipulated";
        assertThrows(JwtService.InvalidJwtException.class, () -> jwtService.parse(invalidToken));
    }
}
