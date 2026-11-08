package ec.twintailcs.electoral.legal.application;

import ec.twintailcs.electoral.acta.domain.Acta;
import ec.twintailcs.electoral.acta.incidencia.domain.IncidenciaJrv;
import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.evidencia.domain.RlsContexto;
import ec.twintailcs.electoral.legal.domain.DpoContactRequest;
import ec.twintailcs.electoral.legal.domain.LegalException;
import ec.twintailcs.electoral.legal.domain.TermsAcceptanceAudit;
import ec.twintailcs.electoral.legal.domain.UserExportLog;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Logica del bloque legal: aceptacion de terminos, exportacion de datos y contacto con el DPO.
 *
 * <p>Las tres responsabilidades viven juntas porque comparten los mismos dos hechos: la version
 * vigente de los documentos y la identidad de quien esta haciendo el pedido.
 */
@ApplicationScoped
public class LegalService {

  /** Ventana del limite de exportacion: una vez cada 24 horas por usuario. */
  public static final Duration VENTANA_EXPORT = Duration.ofHours(24);

  /**
   * Contexto de RLS de la transaccion en curso.
   *
   * <p>Las tres tablas del bloque legal tienen RLS activada y forzada: sin el contexto del usuario,
   * {@code terms_acceptance_audit} rechaza el INSERT (su politica exige {@code usuario_id =
   * get_current_user_id()}) y {@code user_export_log} no devuelve filas (su politica de lectura
   * tambien compara con el id del usuario). Sin activar el contexto, la aceptacion de terminos
   * reventaria con una violacion de RLS y el limite de exportacion nunca veria la exportacion
   * anterior: el usuario podria exportar cada 30 segundos.
   */
  @Inject RlsContexto rls;

  /**
   * Registra la aceptacion de los terminos y de la politica de privacidad.
   *
   * <p>El historico va a {@code terms_acceptance_audit} y el atajo a las columnas del usuario. El
   * tipo de consentimiento distingue la primera aceptacion de la re-aceptacion que dispara un
   * cambio de version.
   *
   * @param usuario usuario que acepta (el caller persiste despues)
   * @param ip direccion del cliente
   * @param userAgent cliente del navegador
   * @param locale idioma del navegador
   * @param versionTerminos version vigente de los terminos
   * @param versionPrivacidad version vigente de la politica
   */
  public void aceptarTerminos(
      Usuario usuario,
      String ip,
      String userAgent,
      String locale,
      String versionTerminos,
      String versionPrivacidad) {

    rls.activar(usuario.getId());

    Instant ahora = Instant.now();
    boolean reAceptacion = usuario.getAcceptedTermsVersion() != null;

    TermsAcceptanceAudit auditoria = new TermsAcceptanceAudit();
    auditoria.setUsuarioId(usuario.getId());
    auditoria.setTermsVersion(versionTerminos);
    auditoria.setPrivacyVersion(versionPrivacidad);
    auditoria.setAcceptedAt(ahora);
    auditoria.setIpAddress(ip);
    auditoria.setUserAgent(recortar(userAgent, 500));
    auditoria.setLocale(recortar(locale, 16));
    auditoria.setConsentType(reAceptacion ? "update" : "initial");
    auditoria.persist();

    usuario.setAcceptedTermsVersion(versionTerminos);
    usuario.setAcceptedTermsAt(ahora);
    usuario.setAcceptedPrivacyVersion(versionPrivacidad);
    usuario.setAcceptedPrivacyAt(ahora);
  }

  /**
   * Exporta los datos personales del usuario (GDPR Art. 15).
   *
   * <p>Solo datos propios: las actas que registro y las incidencias que reporto. Ninguna consulta
   * alcanza a otro observador, asi que la exportacion no puede filtrar datos de terceros por
   * construccion.
   *
   * @param usuario usuario autenticado
   * @return mapa con los datos del usuario y su historial
   */
  public Map<String, Object> exportarDatos(Usuario usuario) {
    rls.activar(usuario.getId());

    Long usuarioId = usuario.getId();

    // La lista se asigna a una variable tipada: el metodo estatico `list` de Panache
    // es generico y sin destino de asignacion el inferidor lo deja en
    // PanacheEntityBase, que no alcanza para el for-each.
    List<Acta> actasDelUsuario = Acta.list("registradaPorId", usuarioId);
    List<Map<String, Object>> actas = new ArrayList<>();
    for (Acta acta : actasDelUsuario) {
      Map<String, Object> fila = new LinkedHashMap<>();
      fila.put("id", acta.getId());
      fila.put("public_id", String.valueOf(acta.getPublicId()));
      fila.put("jrv_id", acta.getJrvId());
      fila.put("cargo_id", acta.getCargoId());
      fila.put("estado", acta.getEstado() == null ? null : acta.getEstado().name());
      fila.put("total_sufragantes", acta.getTotalSufragantes());
      fila.put("votos_blancos", acta.getVotosBlancos());
      fila.put("votos_nulos", acta.getVotosNulos());
      fila.put("fecha_registro", acta.getFechaRegistro());
      actas.add(fila);
    }

    List<IncidenciaJrv> incidenciasDelUsuario = IncidenciaJrv.list("reportadaPorId", usuarioId);
    List<Map<String, Object>> incidencias = new ArrayList<>();
    for (IncidenciaJrv incidencia : incidenciasDelUsuario) {
      Map<String, Object> fila = new LinkedHashMap<>();
      fila.put("id", incidencia.getId());
      fila.put("jrv_id", incidencia.getJrvId());
      fila.put("tipo", incidencia.getTipo() == null ? null : incidencia.getTipo().name());
      fila.put("descripcion", incidencia.getDescripcion());
      fila.put("evidencia_url", incidencia.getEvidenciaUrl());
      fila.put("fecha_registro", incidencia.getFechaRegistro());
      incidencias.add(fila);
    }

    List<UserExportLog> exportacionesDelUsuario = UserExportLog.list("usuarioId", usuarioId);
    List<Map<String, Object>> exportaciones = new ArrayList<>();
    for (UserExportLog log : exportacionesDelUsuario) {
      Map<String, Object> fila = new LinkedHashMap<>();
      fila.put("export_id", log.getExportId());
      fila.put("exported_at", log.getExportedAt());
      fila.put("record_count", log.getRecordCount());
      exportaciones.add(fila);
    }

    Map<String, Object> usuarioJson = new LinkedHashMap<>();
    usuarioJson.put("id", usuario.getId());
    usuarioJson.put("public_id", String.valueOf(usuario.getPublicId()));
    usuarioJson.put("email", usuario.getEmail());
    usuarioJson.put("rol", usuario.getRolNombre());
    usuarioJson.put("first_name", usuario.getNombre());
    usuarioJson.put("last_name", usuario.getApellido());
    usuarioJson.put("created_at", usuario.getCreadoEn());
    usuarioJson.put("accepted_terms_at", usuario.getAcceptedTermsAt());
    usuarioJson.put("accepted_terms_version", usuario.getAcceptedTermsVersion());
    usuarioJson.put("accepted_privacy_at", usuario.getAcceptedPrivacyAt());
    usuarioJson.put("accepted_privacy_version", usuario.getAcceptedPrivacyVersion());

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("exported_at", Instant.now());
    out.put("export_request_id", UUID.randomUUID().toString());
    out.put("user", usuarioJson);
    out.put("actas_registradas", actas);
    out.put("dispute_submissions", incidencias);
    out.put("exports_data", exportaciones);
    return out;
  }

  /**
   * Registra una exportacion en el log.
   *
   * @return el log recien creado
   */
  public UserExportLog registrarExport(
      Long usuarioId, String exportId, String ip, int recordCount) {
    rls.activar(usuarioId);

    UserExportLog log = new UserExportLog();
    log.setUsuarioId(usuarioId);
    log.setExportId(exportId);
    log.setIpAddress(ip);
    log.setRecordCount(recordCount);
    log.persist();
    return log;
  }

  /**
   * Ultima exportacion del usuario, si hubo.
   *
   * <p>El limite de 24 horas sale de aca y no de un contador en memoria: asi se respeta aunque el
   * backend se reinicie o haya mas de una replica.
   */
  public Optional<UserExportLog> ultimaExportacion(Long usuarioId) {
    rls.activar(usuarioId);

    PanacheQuery<UserExportLog> query =
        UserExportLog.find("usuarioId = ?1 order by exportedAt desc", usuarioId);
    return query.page(0, 1).list().stream().findFirst();
  }

  /**
   * Verifica el limite de una exportacion cada 24 horas.
   *
   * @throws LegalException 429 si todavia no se cumple la ventana
   */
  public void exigirVentanaExport(Long usuarioId) {
    Optional<UserExportLog> ultima = ultimaExportacion(usuarioId);
    if (ultima.isEmpty()) {
      return;
    }
    Instant reinicio = ultima.get().getExportedAt().plus(VENTANA_EXPORT);
    long restantes = Duration.between(Instant.now(), reinicio).getSeconds();
    if (restantes > 0) {
      throw LegalException.limiteSuperado(
          "Max 1 export per 24 hours. Retry after " + reinicio, (int) restantes);
    }
  }

  /** Guarda una solicitud al DPO. */
  public DpoContactRequest registrarSolicitudDpo(
      String nombre, String email, String asunto, String mensaje, String ip) {
    DpoContactRequest solicitud = new DpoContactRequest();
    solicitud.setName(nombre);
    solicitud.setEmail(email);
    solicitud.setSubject(asunto);
    solicitud.setMessage(mensaje);
    solicitud.setIpAddress(ip);
    solicitud.persist();
    return solicitud;
  }

  private static String recortar(String valor, int max) {
    if (valor == null) {
      return null;
    }
    return valor.length() <= max ? valor : valor.substring(0, max);
  }
}
