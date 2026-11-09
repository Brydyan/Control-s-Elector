package ec.twintailcs.electoral.auth.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.time.Instant;

/**
 * Resultado de reenviar una invitacion.
 *
 * <p>No trae ninguna credencial: la unica credencial es el link de activacion, que sale por email.
 *
 * @param success siempre true: un fallo llega como error, no como respuesta
 * @param message texto para el operador
 * @param newInvitationSentAt momento del reenvio
 * @param expiresAt momento en que la nueva invitacion deja de valer
 * @param emailPersonal correo al que se reenvio
 */
@RegisterForReflection
public record ResendInvitationResponse(
    boolean success,
    String message,
    Instant newInvitationSentAt,
    Instant expiresAt,
    String emailPersonal) {}
