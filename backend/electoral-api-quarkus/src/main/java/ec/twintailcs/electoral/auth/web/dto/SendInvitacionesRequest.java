package ec.twintailcs.electoral.auth.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

/**
 * Pedido de envio de invitaciones.
 *
 * @param usuarioIds ids publicos (UUID) de los usuarios a invitar. Son los ids publicos y no los
 *     internos: los ids internos no viajan nunca por la API.
 */
@RegisterForReflection
public record SendInvitacionesRequest(@NotEmpty List<UUID> usuarioIds) {}
