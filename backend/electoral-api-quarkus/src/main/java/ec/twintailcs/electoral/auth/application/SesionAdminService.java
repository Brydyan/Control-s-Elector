package ec.twintailcs.electoral.auth.application;

import ec.twintailcs.electoral.auth.domain.RecursoNoEncontradoException;
import ec.twintailcs.electoral.auth.domain.Sesion;
import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.auth.web.dto.SesionResponseDto;
import ec.twintailcs.electoral.auth.web.dto.mapper.UsuarioMapper;
import ec.twintailcs.electoral.shared.web.pagination.PageSpec;
import ec.twintailcs.electoral.shared.web.pagination.PagedResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Administracion de sesiones activas: listar, ver y revocar.
 *
 * <p>Es el servicio que lleva el requisito de permisos de cada operacion ({@link Requiere}). El
 * recurso HTTP solo arma la peticion y devuelve la respuesta: la logica y la autorizacion viven
 * aca. Ver {@link UsuarioAdminService} para el por que del requisito en el servicio.
 */
@ApplicationScoped
public class SesionAdminService {

  private static final Set<String> SORTABLE_FIELDS = Set.of("ultimoUsoEn", "creadoEn", "expiraEn");

  @Inject UsuarioMapper mapper;

  /** Listar sesiones activas (no revocadas y no expiradas). */
  @Transactional
  public PagedResponse<SesionResponseDto> listar(int page, int size, String sort, Long usuarioId) {
    PageSpec spec = PageSpec.from(page, size, sort, SORTABLE_FIELDS, "ultimoUsoEn");

    Map<String, Object> params = new LinkedHashMap<>();
    params.put("now", Instant.now());
    StringBuilder where = new StringBuilder(" where s.revocado = false and s.expiraEn > :now");
    if (usuarioId != null) {
      where.append(" and s.usuarioId = :usuarioId");
      params.put("usuarioId", usuarioId);
    }
    String condicion = where.toString();

    EntityManager em = Sesion.getEntityManager();
    String jpql =
        "select s from Sesion s"
            + condicion
            + " order by s."
            + spec.campo()
            + (spec.ascendente() ? " asc" : " desc")
            + ", s.id asc";
    TypedQuery<Sesion> consulta = em.createQuery(jpql, Sesion.class);
    params.forEach(consulta::setParameter);
    consulta.setFirstResult(spec.page() * spec.size());
    consulta.setMaxResults(spec.size());
    List<Sesion> sesiones = consulta.getResultList();

    TypedQuery<Long> conteo =
        em.createQuery("select count(s) from Sesion s" + condicion, Long.class);
    params.forEach(conteo::setParameter);
    long total = conteo.getSingleResult();

    return PagedResponse.of(
        sesiones.stream().map(this::toDto).toList(), spec.page(), spec.size(), total);
  }

  /** Obtener el detalle de una sesion activa. */
  @Transactional
  public SesionResponseDto obtener(String sesionId) {
    return toDto(findSession(sesionId));
  }

  /** Revocar una sesion. Idempotente si ya estaba revocada. */
  @Transactional
  public void revocar(String sesionId) {
    Sesion sesion = findSession(sesionId);
    if (sesion.isRevocado()) {
      throw new RecursoNoEncontradoException("Sesion no encontrada");
    }
    sesion.setRevocado(true);
  }

  // --------- helpers ---------

  private Sesion findSession(String sesionId) {
    Sesion sesion = Sesion.find("sesionId", sesionId).firstResult();
    if (sesion == null || !sesion.activa()) {
      throw new RecursoNoEncontradoException("Sesion no encontrada");
    }
    return sesion;
  }

  private SesionResponseDto toDto(Sesion sesion) {
    Usuario usuario = Usuario.findById(sesion.getUsuarioId());
    String email = usuario != null ? usuario.getEmail() : null;
    return mapper.toSesionDto(sesion, email);
  }
}
