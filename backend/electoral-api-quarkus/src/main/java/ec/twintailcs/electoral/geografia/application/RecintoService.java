package ec.twintailcs.electoral.geografia.application;

import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.geografia.domain.GeografiaConflictException;
import ec.twintailcs.electoral.geografia.domain.GeografiaNotFoundException;
import ec.twintailcs.electoral.geografia.domain.Jrv;
import ec.twintailcs.electoral.geografia.domain.Parroquia;
import ec.twintailcs.electoral.geografia.domain.Recinto;
import ec.twintailcs.electoral.geografia.domain.RecintoRepository;
import ec.twintailcs.electoral.evidencia.domain.RlsContexto;
import ec.twintailcs.electoral.shared.cache.InvalidadorDeCache;
import ec.twintailcs.electoral.shared.web.pagination.PagedResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Service for Recinto CRUD operations.
 *
 * <p>Validates: - Parroquia exists - Coordinator exists and has COORDINATOR role - Nombre +
 * Parroquia are unique - Cannot delete Recinto with assigned JRVs
 */
@ApplicationScoped
public class RecintoService {

  private static final int MAX_JRVS_POR_RANGO = 1000;

  @Inject RecintoRepository repository;

  @Inject EntityManager entityManager;

  @Inject RlsContexto rls;

  @Inject InvalidadorDeCache invalidador;

  /**
   * Create a new Recinto.
   *
   * @param nombre Recinto name (must be unique per Parroquia)
   * @param parroquiaId Parent Parroquia id
   * @param coordinatorId Coordinator (Usuario) id with COORDINATOR role
   * @param estado Initial state (usually "ACTIVO")
   * @param masterId authenticated MASTER user id used by PostgreSQL RLS
   * @return Created Recinto
   * @throws GeografiaNotFoundException if Parroquia or Coordinator not found
   * @throws GeografiaConflictException if nombre+parroquia already exists or Coordinator invalid
   */
  @Transactional
  public Recinto crearRecinto(
      String nombre, Long parroquiaId, Long coordinatorId, String estado, Long masterId) {
    rls.activar(masterId);
    // Validate Parroquia exists
    Parroquia parroquia = Parroquia.findById(parroquiaId);
    if (parroquia == null) {
      throw new GeografiaNotFoundException("Parroquia " + parroquiaId + " not found");
    }

    // Check unique constraint: nombre + parroquiaId
    if (repository.existsByNombreAndParroquiaId(nombre, parroquiaId)) {
      throw new GeografiaConflictException(
          "Recinto with nombre=" + nombre + " already exists in Parroquia " + parroquiaId);
    }

    // Validate Coordinator if provided
    if (coordinatorId != null) {
      Usuario coordinator = Usuario.findById(coordinatorId);
      if (coordinator == null) {
        throw new GeografiaNotFoundException("Coordinator " + coordinatorId + " not found");
      }
      // Note: Role validation is deferred until role enum is available in auth module
    }

    // Create and persist
    Recinto recinto = new Recinto(nombre, parroquiaId, coordinatorId, estado);
    repository.persist(recinto);
    return recinto;
  }

  /**
   * Update an existing Recinto.
   *
   * @param id Recinto id
   * @param nombre New nombre (optional)
   * @param coordinatorId New Coordinator id (optional)
   * @param estado New estado (optional)
   * @param masterId authenticated MASTER user id used by PostgreSQL RLS
   * @return Updated Recinto
   * @throws GeografiaNotFoundException if Recinto not found
   * @throws GeografiaConflictException if new nombre violates unique constraint or Coordinator
   *     invalid
   */
  @Transactional
  public Recinto actualizarRecinto(
      Long id, String nombre, Long coordinatorId, String estado, Long masterId) {
    rls.activar(masterId);
    Recinto recinto =
        repository
            .findByIdOptional(id)
            .orElseThrow(() -> new GeografiaNotFoundException("Recinto " + id + " not found"));

    // Validate new nombre if provided (must be unique per Parroquia)
    if (nombre != null && !nombre.equals(recinto.getNombre())) {
      if (repository.existsByNombreAndParroquiaId(nombre, recinto.getParroquiaId())) {
        throw new GeografiaConflictException(
            "Recinto with nombre="
                + nombre
                + " already exists in Parroquia "
                + recinto.getParroquiaId());
      }
      recinto.setNombre(nombre);
    }

    // Validate new Coordinator if provided
    if (coordinatorId != null && !coordinatorId.equals(recinto.getCoordinatorId())) {
      Usuario coordinator = Usuario.findById(coordinatorId);
      if (coordinator == null) {
        throw new GeografiaNotFoundException("Coordinator " + coordinatorId + " not found");
      }
      recinto.setCoordinatorId(coordinatorId);
    }

    // Update estado if provided
    if (estado != null && !estado.equals(recinto.getEstado())) {
      recinto.setEstado(estado);
    }

    repository.persist(recinto);
    return recinto;
  }

  /**
   * Get a Recinto by id.
   *
   * @param id Recinto id
   * @return Recinto if found
   * @throws GeografiaNotFoundException if not found
   */
  @Transactional
  public Recinto obtenerRecinto(Long id) {
    return repository
        .findByIdOptional(id)
        .orElseThrow(() -> new GeografiaNotFoundException("Recinto " + id + " not found"));
  }

  /**
   * List all Recintos with pagination.
   *
   * @param page Page number (0-indexed)
   * @param size Page size
   * @return Paged list of Recintos
   */
  @Transactional
  public PagedResponse<Recinto> listarRecintos(int page, int size) {
    long total = repository.count();
    List<Recinto> data =
        repository
            .findAll(io.quarkus.panache.common.Sort.by("id"))
            .page(io.quarkus.panache.common.Page.of(page, size))
            .list();
    return PagedResponse.of(data, page, size, total);
  }

  /**
   * Delete a Recinto.
   *
   * @param id Recinto id
   * @throws GeografiaNotFoundException if not found
   * @throws GeografiaConflictException if Recinto has JRVs assigned
   */
  @Transactional
  public void eliminarRecinto(Long id, Long masterId) {
    rls.activar(masterId);
    Recinto recinto =
        repository
            .findByIdOptional(id)
            .orElseThrow(() -> new GeografiaNotFoundException("Recinto " + id + " not found"));

    // Check if Recinto has JRVs assigned
    long jrvCount = repository.countJrvsInRecinto(id);
    if (jrvCount > 0) {
      throw new GeografiaConflictException(
          "Cannot delete Recinto " + id + " with " + jrvCount + " assigned JRVs");
    }

    repository.deleteById(recinto.getId());
  }

  /**
   * Bulk assign JRVs to a Recinto.
   *
   * @param recintoId Recinto id
   * @param jrvIds List of JRV ids to assign
   * @param masterId authenticated MASTER user id used by PostgreSQL RLS
   * @throws GeografiaNotFoundException if Recinto not found
   */
  @Transactional
  public void asignarJrvsAlRecinto(Long recintoId, List<Long> jrvIds, Long masterId) {
    rls.activar(masterId);
    // Validate Recinto exists
    Recinto recinto =
        repository
            .findByIdOptional(recintoId)
            .orElseThrow(
                () -> new GeografiaNotFoundException("Recinto " + recintoId + " not found"));

    if (jrvIds == null || jrvIds.isEmpty()) {
      return;
    }

    List<Long> idsUnicos = jrvIds.stream().distinct().toList();
    if (idsUnicos.contains(null)) {
      throw new IllegalArgumentException("Los IDs de JRV no pueden ser nulos");
    }

    long jrvsAsignables =
        entityManager
            .createQuery(
                "select count(j) from Jrv j where j.id in :jrvIds and j.parroquiaId = :parroquiaId"
                    + " and (j.recintoId is null or j.recintoId = :recintoId)",
                Long.class)
            .setParameter("jrvIds", idsUnicos)
            .setParameter("parroquiaId", recinto.getParroquiaId())
            .setParameter("recintoId", recintoId)
            .getSingleResult();
    if (jrvsAsignables != idsUnicos.size()) {
      throw new GeografiaConflictException(
          "Una o más JRVs no pertenecen a la parroquia o ya están asignadas a otro recinto");
    }

    entityManager
        .createQuery(
            "update Jrv j set j.recintoId = :recintoId, j.recinto = :recintoNombre"
                + " where j.id in :jrvIds"
                + " and (j.recintoId is null or j.recintoId = :recintoId)")
        .setParameter("recintoId", recintoId)
        .setParameter("recintoNombre", recinto.getNombre())
        .setParameter("jrvIds", idsUnicos)
        .executeUpdate();
    invalidador.invalidar("geografia");
  }

  /**
   * Genera un rango de JRVs para el recinto y activa todos los cargos configurados.
   *
   * <p>La tabla {@code cargo} no tiene un indicador de actividad; cada cargo existente en el
   * catálogo se considera vigente y se habilita mediante {@code jrv_cargo.activo}.
   */
  @Transactional
  public List<Jrv> generarJrvsPorRango(
      Long recintoId,
      int desde,
      int hasta,
      String sufijoGenero,
      int electoresPorMesa,
      Long masterId) {
    rls.activar(masterId);

    long cantidad = (long) hasta - desde + 1;
    if (desde < 1 || hasta < desde || cantidad > MAX_JRVS_POR_RANGO) {
      throw new IllegalArgumentException(
          "El rango debe ser positivo, creciente y no superar " + MAX_JRVS_POR_RANGO + " JRVs");
    }
    if (electoresPorMesa < 1) {
      throw new IllegalArgumentException("Los electores por mesa deben ser mayores que cero");
    }

    String sufijo = sufijoGenero == null ? "" : sufijoGenero.trim();
    if (sufijo.length() > 8 || !sufijo.matches("[A-Za-z0-9]*")) {
      throw new IllegalArgumentException("El sufijo solo puede contener hasta 8 letras o números");
    }

    Recinto recinto =
        repository
            .findByIdOptional(recintoId)
            .orElseThrow(() -> new GeografiaNotFoundException("Recinto " + recintoId + " not found"));
    if (Parroquia.findById(recinto.getParroquiaId()) == null) {
      throw new GeografiaNotFoundException(
          "Parroquia " + recinto.getParroquiaId() + " not found");
    }

    List<String> numeros = new ArrayList<>((int) cantidad);
    for (long numero = desde; numero <= hasta; numero++) {
      String numeroJrv =
          sufijo.isEmpty()
              ? "JRV-" + String.format(Locale.ROOT, "%03d", numero)
              : "JRV-" + sufijo + "-" + String.format(Locale.ROOT, "%03d", numero);
      if (numeroJrv.length() > 20) {
        throw new IllegalArgumentException("El número generado supera los 20 caracteres permitidos");
      }
      numeros.add(numeroJrv);
    }

    long existentes =
        entityManager
            .createQuery(
                "select count(j) from Jrv j where j.parroquiaId = :parroquiaId and j.numero in :numeros",
                Long.class)
            .setParameter("parroquiaId", recinto.getParroquiaId())
            .setParameter("numeros", numeros)
            .getSingleResult();
    if (existentes > 0) {
      throw new GeografiaConflictException(
          "El rango contiene números de JRV que ya existen en esta parroquia");
    }

    List<Jrv> generadas = new ArrayList<>(numeros.size());
    for (String numeroJrv : numeros) {
      Jrv jrv = new Jrv(numeroJrv, recinto.getNombre(), electoresPorMesa, recinto.getParroquiaId());
      jrv.setRecintoId(recintoId);
      jrv.persist();
      entityManager
          .createNativeQuery(
              "insert into jrv_cargo (jrv_id, cargo_id, activo)"
                  + " select :jrvId, c.id, true from cargo c")
          .setParameter("jrvId", jrv.getId())
          .executeUpdate();
      generadas.add(jrv);
    }

    invalidador.invalidar("geografia");
    return List.copyOf(generadas);
  }
}
