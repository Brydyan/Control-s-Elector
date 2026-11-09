package ec.twintailcs.electoral.auth.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.time.Instant;
import java.util.UUID;

/**
 * Resultado de una invitacion.
 *
 * <p>Ninguna credencial viaja aqui. La unica credencial de la invitacion es el link de activacion,
 * que sale por email; el backend no lo devuelve en la respuesta ni lo guarda en claro en ningun
 * lado (solo su HMAC).
 *
 * @param usuarioId id publico del usuario invitado
 * @param username login generado con el que entra al sistema
 * @param emailPersonal correo de contacto al que se mando la invitacion
 * @param status estado de la invitacion: INVITED, CREATED (creada sin email), EXPIRED, ACTIVATED
 * @param expiraEn momento en que la invitacion deja de valer; null si no se envio invitacion
 * @param success siempre true: un fallo llega como error, no como respuesta
 * @param mensaje texto para el operador, distinto segun se haya enviado el email o no
 */
@RegisterForReflection
public record UserInvitationResponse(
    UUID usuarioId,
    String username,
    String emailPersonal,
    String status,
    Instant expiraEn,
    boolean success,
    String mensaje) {}
