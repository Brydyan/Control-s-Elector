package ec.twintailcs.electoral.acta.web;

import ec.twintailcs.electoral.acta.application.NotificacionService;
import ec.twintailcs.electoral.auth.security.Autorizador;
import ec.twintailcs.electoral.auth.security.CurrentUser;
import ec.twintailcs.electoral.electoral.web.ContextoSolicitud;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * REST endpoints for notifications: list pending, mark as read.
 *
 * <p>Notifications inform: - COORDINATOR: when new acta is ENVIADA (pending review) - OBSERVER:
 * when acta is APROBADA or RECHAZADA
 *
 * <p>Clients typically poll this endpoint every 10 seconds (D3 MVP). Phase 2 may implement
 * WebSocket for real-time notifications.
 */
@Path("/api/v1/notifications")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class NotificacionResource {

  @Inject Autorizador autorizador;
  @Inject NotificacionService notificacionService;
  @Inject CurrentUser currentUser;
  @Inject ContextoSolicitud contexto;

  /**
   * Get pending (unread) notifications for the authenticated user.
   *
   * @param limit max number of notifications (default 10)
   * @return list of pending notifications + unread count
   */
  @GET
  @Path("/pendientes")
  public NotificacionesResponseDto obtenerPendientes(
      @QueryParam("limit") @DefaultValue("10") int limit) {
    autorizador.exigir("OBSERVER", "COORDINATOR", "MASTER", "ADMIN");

    Long usuarioId = contexto.usuarioId();

    // Get pending notifications
    List<Object[]> rawNotifications = notificacionService.obtenerPendientes(usuarioId, limit);
    List<NotificacionDto> notificaciones =
        rawNotifications.stream().map(this::mapToNotificacionDto).collect(Collectors.toList());

    // Get unread count
    long pendientes = notificacionService.contarPendientes(usuarioId);

    return new NotificacionesResponseDto(notificaciones, pendientes);
  }

  /**
   * Mark a notification as read.
   *
   * @param notificacionId notification id
   * @return 204 No Content
   */
  @PUT
  @Path("/{notificacionId}/leer")
  public Response marcarComoLeida(@PathParam("notificacionId") Long notificacionId) {
    autorizador.exigir("OBSERVER", "COORDINATOR", "MASTER", "ADMIN");

    notificacionService.marcarComoLeida(notificacionId);
    return Response.noContent().build();
  }

  /** Map raw Object[] row to NotificacionDto. */
  private NotificacionDto mapToNotificacionDto(Object[] row) {
    return new NotificacionDto(
        ((Number) row[0]).longValue(), // id
        (String) row[1], // tipo
        (String) row[2], // titulo
        (String) row[3], // mensaje
        row[4] != null ? ((Number) row[4]).longValue() : null, // actaId
        (Boolean) row[5], // leida
        ((java.sql.Timestamp) row[6]).toInstant() // creado_en
        );
  }

  /** DTO for single notification. */
  public static class NotificacionDto {
    public Long id;
    public String tipo;
    public String titulo;
    public String mensaje;
    public Long actaId;
    public Boolean leida;
    public Instant creadoEn;

    public NotificacionDto(
        Long id,
        String tipo,
        String titulo,
        String mensaje,
        Long actaId,
        Boolean leida,
        Instant creadoEn) {
      this.id = id;
      this.tipo = tipo;
      this.titulo = titulo;
      this.mensaje = mensaje;
      this.actaId = actaId;
      this.leida = leida;
      this.creadoEn = creadoEn;
    }
  }

  /** Response DTO for notifications list. */
  public static class NotificacionesResponseDto {
    public List<NotificacionDto> notificaciones;
    public long pendientes;

    public NotificacionesResponseDto(List<NotificacionDto> notificaciones, long pendientes) {
      this.notificaciones = notificaciones;
      this.pendientes = pendientes;
    }
  }
}
