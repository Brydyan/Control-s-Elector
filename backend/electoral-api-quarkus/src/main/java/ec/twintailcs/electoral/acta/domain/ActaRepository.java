package ec.twintailcs.electoral.acta.domain;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Acceso a datos del modulo de actas.
 *
 * <p>Las consultas de escritura y de carga simple usan JPQL sobre las entidades {@link Acta} y
 * {@link ActaDetalle}. Las consultas de lectura que proyectan datos de otros modulos (nombre del
 * cargo, numero de JRV, votos por candidato, historial de auditoria) usan SQL nativo: esas tablas
 * ({@code jrv}, {@code jrv_cargo}, {@code cargo}, {@code usuario}, {@code audit_log}) pertenecen a
 * modulos que todavia no estan portados, y mapear una segunda entidad sobre las mismas tablas
 * obligaria a mantener dos mapeos en paralelo.
 *
 * <p>El bloqueo pesimista se pide con {@link LockModeType#PESSIMISTIC_WRITE}, que Hibernate
 * traduce a {@code SELECT ... FOR UPDATE}: es el invariante de concurrencia del modulo (dos
 * transiciones simultaneas sobre la misma acta se serializan).
 *
 * <p>Todas las consultas respetan el RLS de Postgres: la tabla {@code acta} solo es visible para
 * el COORDINATOR, el MASTER y el ADMIN, y para el OBSERVER solo las actas que el mismo registro.
 */
@ApplicationScoped
public class ActaRepository {

  /**
   * Proyeccion de un acta: sus datos, el nombre del cargo, el numero de la JRV y el subtotal de
   * votos por candidato (subconsulta correlacionada, sin N+1).
   */
  private static final String SQL_PROYECCION =
      "select a.id, a.jrv_id, a.cargo_id, a.registrada_por, a.estado, a.total_sufragantes,"
          + " a.votos_blancos, a.votos_nulos, a.fecha_registro, a.motivo_anulacion, c.nombre,"
          + " j.numero,"
          + " (select coalesce(sum(d.votos), 0) from acta_detalle d where d.acta_id = a.id)"
          + " from acta a join cargo c on c.id = a.cargo_id join jrv j on j.id = a.jrv_id ";

  @Inject EntityManager entityManager;

  // ---------------------------------------------------------------------------------------------
  // Registros de las proyecciones
  // ---------------------------------------------------------------------------------------------

  /**
   * Fila de la proyección de actas: los datos del acta mas el nombre del cargo, el numero de la JRV
   * y el subtotal de votos por candidato.
   */
  public record ProyeccionActa(
      Long id,
      Long jrvId,
      Long cargoId,
      Long registradaPorId,
      EstadoActa estado,
      Integer totalSufragantes,
      Integer votosBlancos,
      Integer votosNulos,
      Instant fechaRegistro,
      String motivoAnulacion,
      String nombreCargo,
      String numeroJrv,
      Long totalVotosCandidatos) {}

  /** JRV con el resumen de cargos pendientes y registrados. */
  public record FilaJrv(
      Long id, String numero, String parroquia, int cargosPendientes, int cargosRegistrados) {}

  /** Cargo de una JRV con el estado del acta activa de ese par. */
  public record FilaCargo(
      Long id, String nombre, String nivel, EstadoActa estadoActa, Long actaId) {}

  /** Entrada del historial de un acta (viene de {@code audit_log}). */
  public record FilaHistorial(
      Long id,
      String accion,
      Instant timestamp,
      String nombreUsuario,
      String apellidoUsuario,
      String rolUsuario,
      String ipAddress,
      String detalle) {}

  /** Linea del acta con el cargo del candidato (para validar que el candidato pertenece al cargo). */
  public record FilaDetalle(Long candidatoId, Long cargoId, Integer votos) {}

  /** Linea del acta con el nombre del candidato y las siglas del partido (para el expediente PDF). */
  public record FilaVoto(
      Long candidatoId, String candidatoNombre, String partidoSiglas, Integer votos) {}

  /** Datos de cabecera del expediente PDF. */
  public record CabeceraExpediente(
      Long actaId,
      Integer totalSufragantes,
      Integer votosBlancos,
      Integer votosNulos,
      EstadoActa estado,
      String cargoNombre,
      String numeroJrv,
      String recinto,
      String parroquia,
      String canton) {}

  /**
   * Filtros del listado de actas.
   *
   * @param estado estado exacto del acta
   * @param jrvId id interno de la JRV
   * @param cargoId id interno del cargo
   * @param cantonId id interno del canton
   * @param registradaPorId id interno del usuario que registro el acta
   * @param patron patron LIKE ya normalizado (minusculas, wildcards escapados) para la busqueda
   *     libre
   * @param idBusqueda id exacto de acta, cuando la busqueda libre es un numero
   * @param estadoBusqueda estado nombrado por la busqueda libre, cuando el termino nombra un estado
   */
  public record Filtros(
      EstadoActa estado,
      Long jrvId,
      Long cargoId,
      Long cantonId,
      Long registradaPorId,
      String patron,
      Long idBusqueda,
      EstadoActa estadoBusqueda) {}

  // ---------------------------------------------------------------------------------------------
  // Carga de la entidad
  // ---------------------------------------------------------------------------------------------

  /** Acta por id interno. Vacia si no existe o si el RLS la oculta. */
  public Optional<Acta> buscarPorId(Long id) {
    return Optional.ofNullable(Acta.findById(id));
  }

  /**
   * Acta por id interno con bloqueo pesimista ({@code FOR UPDATE}).
   *
   * <p>Es el mecanismo que serializa las transiciones de estado concurrentes sobre la misma acta
   * (disputar / corregir / aprobar / anular).
   */
  public Optional<Acta> buscarPorIdConLock(Long id) {
    Acta acta =
        entityManager
            .createQuery("from Acta a where a.id = :id", Acta.class)
            .setParameter("id", id)
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .getResultStream()
            .findFirst()
            .orElse(null);
    return Optional.ofNullable(acta);
  }

  /**
   * Acta activa (no anulada) del par (JRV, cargo) con bloqueo pesimista.
   *
   * <p>Es la segunda mitad del invariante de concurrencia: si dos observers registran el mismo par
   * a la vez, el que pierde se topa con el indice unico {@code uk_acta_activa} y devuelve 409.
   */
  public Optional<Acta> buscarActivaPorJrvYCargoConLock(Long jrvId, Long cargoId) {
    Acta acta =
        entityManager
            .createQuery(
                "from Acta a where a.jrvId = :jrvId and a.cargoId = :cargoId and a.estado <> :anulada",
                Acta.class)
            .setParameter("jrvId", jrvId)
            .setParameter("cargoId", cargoId)
            .setParameter("anulada", EstadoActa.ANULADA)
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .getResultStream()
            .findFirst()
            .orElse(null);
    return Optional.ofNullable(acta);
  }

  /** Acta activa del par (JRV, cargo) registrada por el usuario indicado. */
  public Optional<Acta> buscarActivaPropia(Long jrvId, Long cargoId, Long observerId) {
    Acta acta =
        entityManager
            .createQuery(
                "from Acta a where a.jrvId = :jrvId and a.cargoId = :cargoId"
                    + " and a.registradaPorId = :observerId and a.estado <> :anulada",
                Acta.class)
            .setParameter("jrvId", jrvId)
            .setParameter("cargoId", cargoId)
            .setParameter("observerId", observerId)
            .setParameter("anulada", EstadoActa.ANULADA)
            .getResultStream()
            .findFirst()
            .orElse(null);
    return Optional.ofNullable(acta);
  }

  /** Borra las lineas del acta. Es un borrado masivo: no deja las entidades en el contexto. */
  public void borrarDetalles(Long actaId) {
    entityManager.createQuery("delete from ActaDetalle d where d.actaId = :actaId")
        .setParameter("actaId", actaId)
        .executeUpdate();
  }

  /** Persiste una linea del acta (votos de un candidato). */
  public void persistirDetalle(Long actaId, Long candidatoId, Integer votos) {
    ActaDetalle detalle = new ActaDetalle(actaId, candidatoId, votos);
    detalle.persist();
  }

  /**
   * Cargo al que pertenece cada candidato pedido.
   *
   * <p>Los candidatos que no existen no aparecen en el mapa: el caso de uso los traduce al error de
   * dominio "Candidato no encontrado".
   */
  public Map<Long, Long> cargosDeCandidatos(List<Long> candidatoIds) {
    List<Long> ids = new ArrayList<>();
    for (Long candidatoId : candidatoIds) {
      if (candidatoId != null && !ids.contains(candidatoId)) {
        ids.add(candidatoId);
      }
    }
    Map<Long, Long> out = new LinkedHashMap<>();
    if (ids.isEmpty()) {
      return out;
    }
    List<Object[]> filas =
        entityManager
            .createNativeQuery("select id, cargo_id from candidato where id in (:ids)", Object[].class)
            .setParameter("ids", ids)
            .getResultList();
    for (Object[] fila : filas) {
      out.put(((Number) fila[0]).longValue(), ((Number) fila[1]).longValue());
    }
    return out;
  }

  // ---------------------------------------------------------------------------------------------
  // Listado de actas
  // ---------------------------------------------------------------------------------------------

  /**
   * Listado de actas con los filtros indicados, ordenado por fecha de registro descendente.
   *
   * <p>Mismo orden que el backend Spring ({@code ORDER BY a.fechaRegistro DESC}).
   */
  public List<ProyeccionActa> listar(Filtros filtros) {
    return listar(filtros, null, null, null);
  }

  /**
   * Listado de actas con orden y paginacion opcionales.
   *
   * @param filtros filtros del listado
   * @param orden clause {@code ORDER BY} completa; si viene {@code null}, se ordena por fecha de
   *     registro descendente
   * @param limit cantidad maxima de filas; {@code null} las devuelve todas
   * @param offset cantidad de filas a saltar; {@code null} no salta ninguna
   */
  public List<ProyeccionActa> listar(
      Filtros filtros, String orden, Long limit, Long offset) {
    Map<String, Object> params = new LinkedHashMap<>();
    String where = where(filtros, params);
    StringBuilder sql = new StringBuilder(SQL_PROYECCION).append(where);
    sql.append(orden == null ? " order by a.fecha_registro desc" : orden);
    if (limit != null) {
      sql.append(" limit :limit");
      params.put("limit", limit);
    }
    if (offset != null) {
      sql.append(" offset :offset");
      params.put("offset", offset);
    }

    jakarta.persistence.Query query =
        entityManager.createNativeQuery(sql.toString(), Object[].class);
    params.forEach(query::setParameter);
    List<Object[]> filas = query.getResultList();
    List<ProyeccionActa> out = new ArrayList<>(filas.size());
    for (Object[] fila : filas) {
      out.add(proyeccion(fila));
    }
    return out;
  }

  /** Proyeccion de un acta concreta. */
  public Optional<ProyeccionActa> proyeccionPorId(Long actaId) {
    String sql = SQL_PROYECCION + " where a.id = :actaId";
    List<Object[]> filas =
        entityManager
            .createNativeQuery(sql, Object[].class)
            .setParameter("actaId", actaId)
            .getResultList();
    if (filas.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(proyeccion(filas.get(0)));
  }

  /** Cantidad de actas que pasan los filtros (sin paginar). */
  public long contar(Filtros filtros) {
    Map<String, Object> params = new LinkedHashMap<>();
    String where = where(filtros, params);
    String sql =
        "select count(*) from acta a join cargo c on c.id = a.cargo_id"
            + " join jrv j on j.id = a.jrv_id "
            + where;
    jakarta.persistence.Query query = entityManager.createNativeQuery(sql);
    params.forEach(query::setParameter);
    return ((Number) query.getSingleResult()).longValue();
  }

  /**
   * Arma la clause {@code WHERE} y sus parametros.
   *
   * <p>Las condiciones se agregan solo cuando el filtro viene: nunca se binds un parametro nulo, que
   * es lo que hace que Postgres no pueda inferir el tipo del placeholder y la consulta entera
   * reviente.
   */
  private String where(Filtros filtros, Map<String, Object> params) {
    StringBuilder where = new StringBuilder("where 1 = 1 ");
    if (filtros == null) {
      return where.toString();
    }
    if (filtros.estado() != null) {
      where.append(" and a.estado = :estado ");
      params.put("estado", filtros.estado().name());
    }
    if (filtros.jrvId() != null) {
      where.append(" and a.jrv_id = :jrvId ");
      params.put("jrvId", filtros.jrvId());
    }
    if (filtros.cargoId() != null) {
      where.append(" and a.cargo_id = :cargoId ");
      params.put("cargoId", filtros.cargoId());
    }
    if (filtros.cantonId() != null) {
      where.append(
          " and exists (select 1 from jrv j2 join parroquia p on p.id = j2.parroquia_id"
              + " where j2.id = a.jrv_id and p.canton_id = :cantonId) ");
      params.put("cantonId", filtros.cantonId());
    }
    if (filtros.registradaPorId() != null) {
      where.append(" and a.registrada_por = :registradaPorId ");
      params.put("registradaPorId", filtros.registradaPorId());
    }
    if (filtros.patron() != null) {
      StringBuilder busqueda = new StringBuilder();
      busqueda.append(" and (lower(j.numero) like :patron escape '\\' ");
      busqueda.append(" or cast(a.id as text) like :patron escape '\\' ");
      if (filtros.idBusqueda() != null) {
        busqueda.append(" or a.id = :idBusqueda ");
        params.put("idBusqueda", filtros.idBusqueda());
      }
      if (filtros.estadoBusqueda() != null) {
        busqueda.append(" or a.estado = :estadoBusqueda ");
        params.put("estadoBusqueda", filtros.estadoBusqueda().name());
      }
      busqueda.append(") ");
      where.append(busqueda);
      params.put("patron", filtros.patron());
    }
    return where.toString();
  }

  /** Convierte una fila de la proyeccion en el registro. */
  private ProyeccionActa proyeccion(Object[] fila) {
    return new ProyeccionActa(
        ((Number) fila[0]).longValue(),
        ((Number) fila[1]).longValue(),
        ((Number) fila[2]).longValue(),
        fila[3] == null ? null : ((Number) fila[3]).longValue(),
        EstadoActa.valueOf((String) fila[4]),
        ((Number) fila[5]).intValue(),
        ((Number) fila[6]).intValue(),
        ((Number) fila[7]).intValue(),
        (Instant) fila[8],
        (String) fila[9],
        (String) fila[10],
        (String) fila[11],
        ((Number) fila[12]).longValue());
  }

  // ---------------------------------------------------------------------------------------------
  // Catalogo de JRVs y cargos
  // ---------------------------------------------------------------------------------------------

  /** Todas las JRVs con su resumen de cargos pendientes y registrados. */
  public List<FilaJrv> listarJrvs() {
    String sql =
        "select j.id, j.numero, p.nombre,"
            + " (select count(*) from jrv_cargo jc where jc.jrv_id = j.id and jc.activo),"
            + " (select count(distinct a.cargo_id) from acta a"
            + "  where a.jrv_id = j.id and a.estado <> 'ANULADA')"
            + " from jrv j join parroquia p on p.id = j.parroquia_id"
            + " order by j.id";
    List<Object[]> filas = entityManager.createNativeQuery(sql, Object[].class).getResultList();
    List<FilaJrv> out = new ArrayList<>();
    for (Object[] fila : filas) {
      int totalCargos = ((Number) fila[3]).intValue();
      int registrados = ((Number) fila[4]).intValue();
      out.add(
          new FilaJrv(
              ((Number) fila[0]).longValue(),
              (String) fila[1],
              (String) fila[2],
              Math.max(0, totalCargos - registrados),
              registrados));
    }
    return out;
  }

  /** Cargos activos de una JRV con el estado del acta activa de cada par (JRV, cargo). */
  public List<FilaCargo> listarCargosDeJrv(Long jrvId) {
    String sql =
        "select c.id, c.nombre, c.nivel, a.estado, a.id"
            + " from jrv_cargo jc join cargo c on c.id = jc.cargo_id"
            + " left join acta a on a.jrv_id = jc.jrv_id and a.cargo_id = c.id"
            + "  and a.estado <> 'ANULADA'"
            + " where jc.jrv_id = :jrvId and jc.activo"
            + " order by c.id";
    List<FilaCargo> out = new ArrayList<>();
    List<Object[]> filas =
        entityManager
            .createNativeQuery(sql, Object[].class)
            .setParameter("jrvId", jrvId)
            .getResultList();
    for (Object[] fila : filas) {
      out.add(
          new FilaCargo(
              ((Number) fila[0]).longValue(),
              (String) fila[1],
              (String) fila[2],
              fila[3] == null ? null : EstadoActa.valueOf((String) fila[3]),
              fila[4] == null ? null : ((Number) fila[4]).longValue()));
    }
    return out;
  }

  /** Indica si el cargo esta habilitado (activo) en la JRV. */
  public boolean cargoHabilitadoEnJrv(Long jrvId, Long cargoId) {
    String sql =
        "select count(*) from jrv_cargo jc join cargo c on c.id = jc.cargo_id"
            + " where jc.jrv_id = :jrvId and jc.cargo_id = :cargoId and jc.activo";
    Object total =
        entityManager
            .createNativeQuery(sql)
            .setParameter("jrvId", jrvId)
            .setParameter("cargoId", cargoId)
            .getSingleResult();
    return ((Number) total).longValue() > 0;
  }

  /** Indica si la JRV existe. */
  public boolean existeJrv(Long jrvId) {
    return contarEnTabla("jrv", "id", jrvId) > 0;
  }

  /** Indica si el cargo existe. */
  public boolean existeCargo(Long cargoId) {
    return contarEnTabla("cargo", "id", cargoId) > 0;
  }

  /** Cantidad de filas de una tabla que cumplen el filtro por id. */
  private long contarEnTabla(String tabla, String columna, Long id) {
    Object total =
        entityManager
            .createNativeQuery("select count(*) from " + tabla + " where " + columna + " = :id")
            .setParameter("id", id)
            .getSingleResult();
    return ((Number) total).longValue();
  }

  // ---------------------------------------------------------------------------------------------
  // Lineas del acta
  // ---------------------------------------------------------------------------------------------

  /** Lineas del acta con el cargo de cada candidato. */
  public List<FilaDetalle> detallesDe(Long actaId) {
    String sql =
        "select d.candidato_id, c.cargo_id, d.votos"
            + " from acta_detalle d join candidato c on c.id = d.candidato_id"
            + " where d.acta_id = :actaId order by d.id";
    List<FilaDetalle> out = new ArrayList<>();
    List<Object[]> filas =
        entityManager
            .createNativeQuery(sql, Object[].class)
            .setParameter("actaId", actaId)
            .getResultList();
    for (Object[] fila : filas) {
      out.add(
          new FilaDetalle(
              ((Number) fila[0]).longValue(),
              ((Number) fila[1]).longValue(),
              ((Number) fila[2]).intValue()));
    }
    return out;
  }

  /** Lineas del acta con el nombre del candidato y las siglas del partido (expediente PDF). */
  public List<FilaVoto> votosPorCandidato(Long actaId) {
    String sql =
        "select d.candidato_id, cand.nombre, p.siglas, d.votos"
            + " from acta_detalle d"
            + " join candidato cand on cand.id = d.candidato_id"
            + " join partido p on p.id = cand.partido_id"
            + " where d.acta_id = :actaId order by d.id";
    List<FilaVoto> out = new ArrayList<>();
    List<Object[]> filas =
        entityManager
            .createNativeQuery(sql, Object[].class)
            .setParameter("actaId", actaId)
            .getResultList();
    for (Object[] fila : filas) {
      out.add(
          new FilaVoto(
              ((Number) fila[0]).longValue(),
              (String) fila[1],
              (String) fila[2],
              ((Number) fila[3]).intValue()));
    }
    return out;
  }

  /** Datos de cabecera del expediente PDF. */
  public Optional<CabeceraExpediente> cabeceraExpediente(Long actaId) {
    String sql =
        "select a.id, a.total_sufragantes, a.votos_blancos, a.votos_nulos, a.estado, c.nombre,"
            + " j.numero, j.recinto, p.nombre, cant.nombre"
            + " from acta a"
            + " join cargo c on c.id = a.cargo_id"
            + " join jrv j on j.id = a.jrv_id"
            + " join parroquia p on p.id = j.parroquia_id"
            + " join canton cant on cant.id = p.canton_id"
            + " where a.id = :actaId";
    List<Object[]> filas =
        entityManager
            .createNativeQuery(sql, Object[].class)
            .setParameter("actaId", actaId)
            .getResultList();
    if (filas.isEmpty()) {
      return Optional.empty();
    }
    Object[] fila = filas.get(0);
    return Optional.of(
        new CabeceraExpediente(
            ((Number) fila[0]).longValue(),
            ((Number) fila[1]).intValue(),
            ((Number) fila[2]).intValue(),
            ((Number) fila[3]).intValue(),
            EstadoActa.valueOf((String) fila[4]),
            (String) fila[5],
            (String) fila[6],
            (String) fila[7],
            (String) fila[8],
            (String) fila[9]));
  }

  // ---------------------------------------------------------------------------------------------
  // Historial de auditoria del acta
  // ---------------------------------------------------------------------------------------------

  /** Transiciones auditadas del acta, de la mas reciente a la mas antigua. */
  public List<FilaHistorial> historialDeActa(Long actaId) {
    String sql =
        "select a.id, a.accion, a.timestamp, u.nombre, u.apellido, r.nombre, a.ip_address,"
            + " a.detalle::text"
            + " from audit_log a"
            + " left join usuario u on u.id = a.registrado_por"
            + " left join rol r on r.id = u.rol_id"
            + " where a.entidad = 'acta' and a.entidad_id = :actaId"
            + " order by a.timestamp desc";
    List<FilaHistorial> out = new ArrayList<>();
    List<Object[]> filas =
        entityManager
            .createNativeQuery(sql, Object[].class)
            .setParameter("actaId", actaId)
            .getResultList();
    for (Object[] fila : filas) {
      out.add(
          new FilaHistorial(
              ((Number) fila[0]).longValue(),
              (String) fila[1],
              (Instant) fila[2],
              (String) fila[3],
              (String) fila[4],
              (String) fila[5],
              (String) fila[6],
              (String) fila[7]));
    }
    return out;
  }

  /** Detalle del ultimo movimiento de disputa del acta (el motivo con el que se disputo). */
  public Optional<String> ultimoMotivoDisputa(Long actaId) {
    String sql =
        "select a.detalle::text from audit_log a"
            + " where a.entidad = 'acta' and a.entidad_id = :actaId and a.accion like '%DISPUTA%'"
            + " order by a.timestamp desc limit 1";
    List<Object[]> filas =
        entityManager
            .createNativeQuery(sql, Object[].class)
            .setParameter("actaId", actaId)
            .getResultList();
    if (filas.isEmpty()) {
      return Optional.empty();
    }
    return Optional.ofNullable((String) filas.get(0)[0]);
  }
}
