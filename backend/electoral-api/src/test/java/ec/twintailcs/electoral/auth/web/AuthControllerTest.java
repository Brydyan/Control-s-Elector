package ec.twintailcs.electoral.auth.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jwt.JWTClaimsSet;
import ec.twintailcs.electoral.auth.application.AuthService;
import ec.twintailcs.electoral.auth.application.JwtService;
import ec.twintailcs.electoral.auth.application.RegisterService;
import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.auth.domain.UsuarioRepository;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private RegisterService registerService;

    @MockitoBean
    private UsuarioRepository usuarioRepository;

    @MockitoBean
    private JwtService jwtService;

    @Test
    void login_withValidCredentials_returnsAuthResponseAndCookie() throws Exception {
        UUID publicId = UUID.randomUUID();
        Usuario usuario = new Usuario();
        usuario.setId(100L);
        usuario.setPublicId(publicId);
        usuario.setEmail("user@example.com");
        usuario.setRol("OBSERVER");

        JwtService.TokenPair tokenPair = new JwtService.TokenPair(
                "accessTokenVal", "refreshTokenVal",
                Duration.ofMinutes(15), Duration.ofDays(7),
                "session-123"
        );

        when(authService.login(eq("user@example.com"), eq("password123"), any(), any()))
                .thenReturn(tokenPair);
        when(usuarioRepository.findByEmail("user@example.com")).thenReturn(Optional.of(usuario));

        String requestBody = "{\"email\":\"user@example.com\",\"password\":\"password123\"}";

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("accessTokenVal"))
                .andExpect(jsonPath("$.usuario.id").value(publicId.toString()))
                .andExpect(jsonPath("$.usuario.email").value("user@example.com"))
                .andExpect(jsonPath("$.usuario.rol").value("OBSERVER"))
                .andExpect(cookie().value("refresh_token", "refreshTokenVal"))
                .andExpect(cookie().httpOnly("refresh_token", true));
    }

    @Test
    void refresh_withValidCookie_returnsNewAuthResponseAndCookie() throws Exception {
        UUID publicId = UUID.randomUUID();
        Usuario usuario = new Usuario();
        usuario.setId(100L);
        usuario.setPublicId(publicId);
        usuario.setEmail("user@example.com");
        usuario.setRol("OBSERVER");

        JwtService.TokenPair tokenPair = new JwtService.TokenPair(
                "newAccessTokenVal", "newRefreshTokenVal",
                Duration.ofMinutes(15), Duration.ofDays(7),
                "session-123"
        );

        when(authService.refresh(eq("refreshTokenVal"))).thenReturn(tokenPair);

        // Mocks for parsing new access token subject (which is the user public ID)
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject(publicId.toString())
                .build();
        JwtService.ParsedJwt parsedJwt = new JwtService.ParsedJwt(claimsSet);
        when(jwtService.parse(eq("newAccessTokenVal"))).thenReturn(parsedJwt);
        when(usuarioRepository.findByPublicId(publicId)).thenReturn(Optional.of(usuario));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refresh_token", "refreshTokenVal")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("newAccessTokenVal"))
                .andExpect(jsonPath("$.usuario.id").value(publicId.toString()))
                .andExpect(jsonPath("$.usuario.email").value("user@example.com"))
                .andExpect(cookie().value("refresh_token", "newRefreshTokenVal"));
    }

    @Test
    void logout_withValidCookie_revokesSessionAndClearsCookie() throws Exception {
        doNothing().when(authService).logout(eq("refreshTokenVal"));

        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(new Cookie("refresh_token", "refreshTokenVal")))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("refresh_token", 0));
    }
}
