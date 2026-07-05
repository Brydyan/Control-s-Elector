package ec.twintailcs.electoral.auth.web;

import ec.twintailcs.electoral.auth.application.AuthService;
import ec.twintailcs.electoral.auth.application.JwtService;
import ec.twintailcs.electoral.auth.application.JwtService.InvalidJwtException;
import ec.twintailcs.electoral.auth.application.RegisterService;
import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.auth.domain.UsuarioRepository;
import ec.twintailcs.electoral.auth.web.dto.AuthResponse;
import ec.twintailcs.electoral.auth.web.dto.LoginRequest;
import ec.twintailcs.electoral.auth.web.dto.RefreshRequest;
import ec.twintailcs.electoral.auth.web.dto.RegisterRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de autenticacion.
 *
 * Convenciones:
 *  - access token: viaja solo en Authorization: Bearer <token> en cada
 *    request subsecuente.
 *  - refresh token: viaja en cookie httpOnly "refresh_token" para que
 *    no sea accesible desde JS. Tambien puede venir en body para
 *    clientes no-browser (Flutter mobile). Misma logica para ambos.
 *
 * Los tres refresh-token sources (cookie, body, header) se prueban en
 * ese orden en refresh(). Logout solo opera con el refresh en cookie o
 * body (no requiere auth access; revocacion es idempotente).
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    /** Nombre canonico de la cookie que transporta el refresh token. */
    public static final String REFRESH_COOKIE = "refresh_token";
    /** TTL de la cookie refresh en segundos; debe matchear refreshTtl. */
    public static final long REFRESH_COOKIE_MAX_AGE_SEC = 7 * 24 * 3600L;

    private final AuthService auth;
    private final RegisterService registros;
    private final UsuarioRepository usuarios;
    private final JwtService jwt;

    public AuthController(AuthService auth, RegisterService registros,
                          UsuarioRepository usuarios,
                          JwtService jwt) {
        this.auth = auth;
        this.registros = registros;
        this.usuarios = usuarios;
        this.jwt = jwt;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest body,
                                              HttpServletRequest req) {
        JwtService.TokenPair pair = auth.login(
                body.email(), body.password(),
                req.getRemoteAddr(), req.getHeader("User-Agent"));

        Usuario u = usuarios.findByEmail(body.email()).orElseThrow();
        // Permisos NO se exponen en el body — quedan en el JWT (claim "perms")
        // para uso interno (JwtAuthFilter -> @PreAuthorize). El FE obtiene
        // el sidebar via GET /api/v1/menus/me.

        AuthResponse payload = new AuthResponse(
                pair.access(), "Bearer",
                pair.accessTtl().toSeconds(),
                pair.sesionId(),
                new AuthResponse.UsuarioActual(u.getId(), u.getEmail(), u.getRol())
        );

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, buildRefreshCookie(pair.refresh()))
                .body(payload);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@RequestBody(required = false) RefreshRequest body,
                                                HttpServletRequest req) {
        String refresh = readRefresh(req, body);
        JwtService.TokenPair pair = auth.refresh(refresh);

        // Necesitamos los datos del usuario para construir la respuesta.
        Long userId = Long.parseLong(jwt.parse(pair.access()).subject());
        Usuario u = usuarios.findById(userId).orElseThrow();

        AuthResponse payload = new AuthResponse(
                pair.access(), "Bearer",
                pair.accessTtl().toSeconds(),
                pair.sesionId(),
                new AuthResponse.UsuarioActual(u.getId(), u.getEmail(), u.getRol())
        );

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, buildRefreshCookie(pair.refresh()))
                .body(payload);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) RefreshRequest body,
                                       HttpServletRequest req) {
        String refresh = readRefresh(req, body);
        auth.logout(refresh);
        // Borrar la cookie (Max-Age=0)
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE,
                        REFRESH_COOKIE + "=; HttpOnly; Secure; SameSite=Strict; Path=/; Max-Age=0")
                .build();
    }

    /** Informacion del usuario actual (parseado del access token en el SecurityContext). */
    @GetMapping("/me")
    public AuthResponse.UsuarioActual me(org.springframework.security.core.Authentication auth) {
        String email = auth.getName();
        Usuario u = usuarios.findByEmail(email).orElseThrow();
        return new AuthResponse.UsuarioActual(u.getId(), u.getEmail(), u.getRol());
    }

    /**
     * Solo MASTER puede crear usuarios. Para esta version es asi (el FE
     * tendra un panel admin que lista usuarios). Cuando existan
     * auto-registros via invite, se cambia esta firma.
     */
    @PostMapping("/register")
    @PreAuthorize("hasAuthority('usuario:create') or hasAuthority('MASTER')")
    public ResponseEntity<AuthResponse.UsuarioActual> register(@Valid @RequestBody RegisterRequest body) {
        Usuario u = registros.register(body.email(), body.password(), body.rol());
        return ResponseEntity.status(201).body(
                new AuthResponse.UsuarioActual(u.getId(), u.getEmail(), u.getRol()));
    }

    // --------- helpers ---------

    /** Lee refresh token de cookie, body o ambos. Devuelve null-safe. */
    private String readRefresh(HttpServletRequest req, RefreshRequest body) {
        if (req.getCookies() != null) {
            for (var c : req.getCookies()) {
                if (REFRESH_COOKIE.equals(c.getName())) {
                    return c.getValue();
                }
            }
        }
        if (body != null && body.refreshToken() != null) {
            return body.refreshToken();
        }
        throw new AuthService.AuthException("No se encontro refresh token");
    }

    /** Cookie httpOnly + Secure + SameSite=Strict. Max-Age configura el TTL del refresh. */
    private String buildRefreshCookie(String value) {
        return REFRESH_COOKIE + "=" + value
                + "; HttpOnly; Secure; SameSite=Strict; Path=/"
                + "; Max-Age=" + REFRESH_COOKIE_MAX_AGE_SEC;
    }

    // ─── Mapeo excepciones → JSON 401 ─────────────────────────────────────────

    @ExceptionHandler(AuthService.AuthException.class)
    public ResponseEntity<Map<String, Object>> authError(AuthService.AuthException ex) {
        return errorBody(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(InvalidJwtException.class)
    public ResponseEntity<Map<String, Object>> badJwt(InvalidJwtException ex) {
        return errorBody(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    private static ResponseEntity<Map<String, Object>> errorBody(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }
}
