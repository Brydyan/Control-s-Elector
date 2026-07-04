package ec.twintailcs.electoral.auth.interfaces.dto;

/**
 * Body para POST /auth/refresh.
 * El refresh token NO viaja en JSON: viaja en cookie httpOnly por seguridad.
 * Ver AuthController.refresh() para la extraccion.
 */
public record RefreshRequest(String refreshToken) {}
