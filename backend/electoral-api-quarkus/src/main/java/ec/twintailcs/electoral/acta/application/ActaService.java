package ec.twintailcs.electoral.acta.application;

import ec.twintailcs.electoral.acta.application.usecase.AnularActaUseCase;
import ec.twintailcs.electoral.acta.application.usecase.AprobarActaUseCase;
import ec.twintailcs.electoral.acta.application.usecase.ConsultarActaDetalleUseCase;
import ec.twintailcs.electoral.acta.application.usecase.CorregirActaUseCase;
import ec.twintailcs.electoral.acta.application.usecase.EnviarActaUseCase;
import ec.twintailcs.electoral.acta.application.usecase.GuardarBorradorActaUseCase;
import ec.twintailcs.electoral.acta.application.usecase.MarcarEnDisputaActaUseCase;
import ec.twintailcs.electoral.acta.application.usecase.RegistrarActaUseCase;
import ec.twintailcs.electoral.acta.domain.Acta;
import ec.twintailcs.electoral.acta.domain.ActaInvalidaException;
import ec.twintailcs.electoral.acta.domain.ActaRepository;
import ec.twintailcs.electoral.acta.domain.EstadoActa;
import ec.twintailcs.electoral.acta.web.ActaPageSpec;
import ec.twintailcs.electoral.acta.web.dto.ActaDetalleCompletoDto;
import ec.twintailcs.electoral.acta.web.dto.ActaRequestDto;
import ec.twintailcs.electoral.acta.web.dto.ActaResponseDto;
import ec.twintailcs.electoral.acta.web.dto.CargoConEstadoDto;
import ec.twintailcs.electoral.acta.web.dto.HistorialActaDto;
import ec.twintailcs.electoral.acta.web.dto.JrvConEstadoDto;
import ec.twintailcs.electoral.acta.web.dto.mapper.ActaMapper;
import ec.twintailcs.electoral.auth.domain.AccesoNoPermitidoException;
import ec.twintailcs.electoral.auth.security.CurrentUser;
import ec.twintailcs.electoral.electoral.web.ContextoSolicitud;
import ec.twintailcs.electoral.evidencia.application.ArchivoEvidencia;
import ec.twintailcs.electoral.evidencia.application.usecase.SubirEvidenciaUseCase;
import ec.twintailcs.electoral.evidencia.domain.Evidencia;
import ec.twintailcs.electoral.evidencia.domain.RlsContexto;
import ec.twintailcs.electoral.shared.web.pagination.PagedResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Locale;

/**
 * Fachada del modulo de actas: logica de negocio y ciclo de vida de los actas.
 *
 * <p>Delega en casos de uso de responsabilidad unica y arma los DTOs de respuesta.
 *
 * <p><b>Por que la transaccion vive aca y no en el resource.</b> El backend Spring mapea el DTO en
 * el controlador, con la sesion de JPA todavia abierta porque la transaccion del caso de uso ya
 * termino. Aca el equivalente es mapear dentro de la transaccion: el DTO lee las filas de la base y
 * el estado del RLS depende de la conexion de la transaccion. Por eso los metodos de la fachada son
 * transaccionales, activan el contexto de RLS del usuario y arman el DTO antes de devolverlo.
 */
@ApplicationScoped
public class ActaService {

  @Inject ContextoSolicitud contexto;
  @Inject CurrentUser currentUser;
  @Inject RlsContexto rls;
  @Inject ActaRepository repository;
  @Inject ActaMapper mapper;
  @Inject ActaAuditService audit;

  @Inject RegistrarActaUseCase registrarActaUseCase;
  @Inject GuardarBorradorActaUseCase guardarBorradorActaUseCase;
  @Inject EnviarActaUseCase enviarActaUseCase;
  @Inject CorregirActaUseCase corregirActaUseCase;
  @Inject MarcarEnDisputaActaUseCase marcarEnDisputaActaUseCase;
  @Inject AprobarActaUseCase aprobarActaUseCase;
  @Inject AnularActaUseCase anularActaUseCase;
  @Inject ConsultarActaDetalleUseCase consultarActaDetalleUseCase;
  @Inject ExpedientePdfService expedientePdfService;
  @Inject SubirEvidenciaUseCase subirEvidenciaUseCase;

  // ---------------------------------------------------------------------------------------------
  // OBSERVER — consultas
  // ---------------------------------------------------------------------------------------------

  /** Actas registradas por el observer autenticado, con filtro opcional por estado. */
  @Transactional
  public List<ActaResponseDto> misActas(EstadoActa estado) {
    Long observerId = activarContexto();
    return repository
        .listar(new ActaRepository.Filtros(estado, null, null, null, observerId, null, null, null))
        .stream()
        .map(mapper::toDtoSinSubtotal)
        .toList();
  }

  /** Actas registradas por el observer autenticado, paginadas con filtro opcional. */
  @Transactional
  public PagedResponse<ActaResponseDto> misActasPaginadas(
      EstadoActa estado, int page, int size) {
    Long observerId = activarContexto();
    ActaRepository.Filtros filtros =
        new ActaRepository.Filtros(estado, null, null, null, observerId, null, null, null);

    long total = repository.contar(filtros);
    long offset = (long) page * size;

    List<ActaResponseDto> data =
        repository
            .listar(filtros, null, (long) size, offset)
            .stream()
            .map(mapper::toDtoSinSubtotal)
            .toList();

    return PagedResponse.of(data, page, size, total);
  }

  /** Actas en estado EN_DISPUTA que el observer autenticado tiene que corregir. */
  @Transactional
  public List<ActaResponseDto> correccionesPendientes() {
    Long observerId = activarContexto();
    return repository
        .listar(
            new ActaRepository.Filtros(
                EstadoActa.EN_DISPUTA, null, null, null, observerId, null, null, null))
        .stream()
        .map(mapper::toDtoSinSubtotal)
        .toList();
  }

  /** Actas en estado EN_DISPUTA paginadas, que el observer autenticado tiene que corregir. */
  @Transactional
  public PagedResponse<ActaResponseDto> correccionesPendientesPaginadas(int page, int size) {
    Long observerId = activarContexto();
    ActaRepository.Filtros filtros =
        new ActaRepository.Filtros(
            EstadoActa.EN_DISPUTA, null, null, null, observerId, null, null, null);

    long total = repository.contar(filtros);
    long offset = (long) page * size;

    List<ActaResponseDto> data =
        repository
            .listar(filtros, null, (long) size, offset)
            .stream()
            .map(mapper::toDtoSinSubtotal)
            .toList();

    return PagedResponse.of(data, page, size, total);
  }

  /** Todas las JRVs con su estado de registro. */
  @Transactional
  public List<JrvConEstadoDto> jrvs() {
    activarContexto();
    return repository.listarJrvs().stream()
        .map(
            fila ->
                new JrvConEstadoDto(
                    fila.id(),
                    fila.numero(),
                    fila.parroquia(),
                    fila.cargosPendientes(),
                    fila.cargosRegistrados()))
        .toList();
  }

  /** Cargos activos de una JRV con el estado del acta de cada par. */
  @Transactional
  public List<CargoConEstadoDto> cargosDeJrv(Long jrvId) {
    activarContexto();
    return repository.listarCargosDeJrv(jrvId).stream()
        .map(
            fila ->
                fila.estadoActa() == null
                    ? CargoConEstadoDto.sinRegistrar(fila.id(), fila.nombre(), fila.nivel())
                    : CargoConEstadoDto.registrado(
                        fila.id(), fila.nombre(), fila.nivel(), fila.estadoActa(), fila.actaId()))
        .toList();
  }

  // ---------------------------------------------------------------------------------------------
  // OBSERVER — escritura
  // ---------------------------------------------------------------------------------------------

  /** Registra el acta y la envia (estado ENVIADA). */
  @Transactional
  public ActaResponseDto registrar(ActaRequestDto dto, String xForwardedFor) {
    Long observerId = activarContexto();
    Acta acta = registrarActaUseCase.execute(dto, observerId, contexto.ip(xForwardedFor));
    return mapper.toDtoSinSubtotal(proyeccionDe(acta));
  }

  /** Guarda o reanuda un borrador (estado BORRADOR). */
  @Transactional
  public ActaResponseDto guardarBorrador(ActaRequestDto dto, String xForwardedFor) {
    Long observerId = activarContexto();
    Acta acta = guardarBorradorActaUseCase.execute(dto, observerId, contexto.ip(xForwardedFor));
    return mapper.toDtoSinSubtotal(proyeccionDe(acta));
  }

  /** Envia el borrador (BORRADOR → ENVIADA). */
  @Transactional
  public ActaResponseDto enviar(Long actaId, String xForwardedFor) {
    Long observerId = activarContexto();
    Acta acta = enviarActaUseCase.execute(actaId, observerId, contexto.ip(xForwardedFor));
    return mapper.toDtoSinSubtotal(proyeccionDe(acta));
  }

  /** Corrige un acta en disputa (EN_DISPUTA → CORREGIDA). */
  @Transactional
  public ActaResponseDto corregir(
      Long actaId, ActaRequestDto dto, boolean esCoordinator, String xForwardedFor) {
    Long usuarioId = activarContexto();
    Acta acta =
        corregirActaUseCase.execute(
            actaId, dto, usuarioId, esCoordinator, contexto.ip(xForwardedFor));
    return mapper.toDtoSinSubtotal(proyeccionDe(acta));
  }

  /** Detalle completo de un acta, con la comprobacion de autoria del observer. */
  @Transactional
  public ActaDetalleCompletoDto verMiActa(Long actaId) {
    Long observerId = activarContexto();
    Acta acta =
        repository
            .buscarPorId(actaId)
            .orElseThrow(() -> new ActaInvalidaException("Acta no encontrada: " + actaId));

    // Autoria: el OBSERVER solo ve SUS actas.
    if (!acta.getRegistradaPorId().equals(observerId)) {
      throw new AccesoNoPermitidoException("Solo puedes consultar tus propias actas");
    }

    return consultarActaDetalleUseCase.ejecutar(actaId);
  }

  /** Sube la fotografia del acta como evidencia. */
  @Transactional
  public void subirImagen(Long actaId, ArchivoEvidencia archivo, String xForwardedFor) {
    Long usuarioId = activarContexto();
    subirEvidenciaUseCase.execute(
        archivo, actaId, usuarioId, currentUser.rol(), contexto.ip(xForwardedFor));
  }

  // ---------------------------------------------------------------------------------------------
  // COORDINATOR — consultas
  // ---------------------------------------------------------------------------------------------

  /**
   * Listado paginado de actas, con filtros y busqueda libre.
   *
   * <p>Semantica: {@code dignidad} es alias de {@code cargo} ({@code dignidadId} se acepta como
   * alias de {@code cargoId}; el {@code cargoId} explicito gana si vienen los dos). La busqueda
   * libre matchea sin distincion de mayusculas contra el numero de la JRV y el id del acta como
   * texto, mas el match exacto por id cuando el termino es un numero y el match exacto por estado
   * cuando el termino nombra un estado. El orden por defecto es {@code fechaRegistro DESC}.
   */
  @Transactional
  public PagedResponse<ActaResponseDto> listarActas(
      EstadoActa estado,
      Long jrvId,
      Long cargoId,
      Long cantonId,
      Long dignidadId,
      String search,
      int page,
      int size,
      String sort,
      String dir) {
    activarContexto();

    ActaPageSpec spec = ActaPageSpec.from(page, size, sort, dir);

    Long cargoFiltro = cargoId != null ? cargoId : dignidadId;
    String searchNorm = (search == null || search.isBlank()) ? null : search.trim();

    ActaRepository.Filtros filtros =
        new ActaRepository.Filtros(
            estado,
            jrvId,
            cargoFiltro,
            cantonId,
            null,
            toLikePattern(searchNorm),
            parseSearchId(searchNorm),
            parseEstadoSearch(searchNorm));

    long total = repository.contar(filtros);
    List<ActaResponseDto> datos =
        repository.listar(filtros, spec.orden(), (long) spec.size(), spec.offset()).stream()
            .map(mapper::toDto)
            .toList();

    return PagedResponse.of(datos, spec.page(), spec.size(), total);
  }

  /** Detalle completo del acta. */
  @Transactional
  public ActaDetalleCompletoDto detalle(Long actaId) {
    activarContexto();
    return consultarActaDetalleUseCase.ejecutar(actaId);
  }

  /** Transiciones auditadas del acta, de la mas reciente a la mas antigua. */
  @Transactional
  public List<HistorialActaDto> historial(Long actaId) {
    activarContexto();
    return repository.historialDeActa(actaId).stream().map(mapper::toHistorialDto).toList();
  }

  // ---------------------------------------------------------------------------------------------
  // COORDINATOR — escritura
  // ---------------------------------------------------------------------------------------------

  /** Marca el acta en disputa (ENVIADA/CORREGIDA → EN_DISPUTA). */
  @Transactional
  public ActaResponseDto disputar(Long actaId, String motivo, String xForwardedFor) {
    Long coordinatorId = activarContexto();
    Acta acta =
        marcarEnDisputaActaUseCase.execute(
            actaId, coordinatorId, motivo, contexto.ip(xForwardedFor));
    return mapper.toDto(proyeccionDe(acta));
  }

  /** Corrige el acta en disputa (EN_DISPUTA → CORREGIDA). */
  @Transactional
  public ActaResponseDto corregirComoCoordinator(
      Long actaId, ActaRequestDto dto, String xForwardedFor) {
    return corregir(actaId, dto, true, xForwardedFor);
  }

  /** Aprueba el acta (ENVIADA/CORREGIDA → APROBADA). */
  @Transactional
  public ActaResponseDto aprobar(Long actaId, String xForwardedFor) {
    Long coordinatorId = activarContexto();
    Acta acta = aprobarActaUseCase.execute(actaId, coordinatorId, contexto.ip(xForwardedFor));
    return mapper.toDto(proyeccionDe(acta));
  }

  /** Anula el acta (APROBADA → ANULADA). Solo el MASTER. */
  @Transactional
  public void anular(Long actaId, String motivo, String xForwardedFor) {
    Long masterId = activarContexto();
    anularActaUseCase.execute(actaId, masterId, motivo, contexto.ip(xForwardedFor));
  }

  /**
   * Sube la fotografia del acta como contingencia del coordinator.
   *
   * <p>Es el camino de emergencia: el OBSERVER no pudo subir la foto y el COORDINATOR toma el
   * relevo. Ademas de la subida normal, deja una fila de auditoria forense con la accion {@code
   * EVIDENCIA_CONTINGENCIA_COORDINADOR}.
   */
  @Transactional
  public void subirEvidenciaDeContingencia(
      Long actaId, ArchivoEvidencia archivo, String xForwardedFor) {
    Long usuarioId = activarContexto();
    String ip = contexto.ip(xForwardedFor);
    Evidencia evidencia =
        subirEvidenciaUseCase.execute(archivo, actaId, usuarioId, currentUser.rol(), ip);

    audit.registrarConIp(
        usuarioId,
        "EVIDENCIA_CONTINGENCIA_COORDINADOR",
        "evidencia",
        evidencia.getId(),
        "Subida de contingencia por COORDINATOR/MASTER: acta="
            + actaId
            + ", s3Key="
            + evidencia.getS3Key(),
        ip);
  }

  /** PDF del expediente de impugnacion CNE. */
  @Transactional
  public byte[] expedientePdf(Long actaId) {
    Long usuarioId = activarContexto();
    return expedientePdfService.generarExpedientePdf(actaId, usuarioId);
  }

  // ---------------------------------------------------------------------------------------------
  // Internos
  // ---------------------------------------------------------------------------------------------

  /**
   * Activa el contexto de RLS del usuario autenticado y devuelve su id interno.
   *
   * <p>Las tablas {@code acta}, {@code acta_detalle}, {@code evidencia} y {@code audit_log} tienen
   * RLS activada y forzada: sin el contexto del usuario, Postgres no devuelve filas y no acepta
   * inserts.
   */
  private Long activarContexto() {
    Long usuarioId = contexto.usuarioId();
    rls.activar(usuarioId);
    return usuarioId;
  }

  /** Proyeccion (cargo, JRV, subtotal) del acta, para armar el DTO de respuesta. */
  private ActaRepository.ProyeccionActa proyeccionDe(Acta acta) {
    return repository
        .proyeccionPorId(acta.getId())
        .orElseThrow(() -> new ActaInvalidaException("Acta no encontrada: " + acta.getId()));
  }

  /**
   * Arma el patron LIKE de la busqueda libre.
   *
   * <p>Se calcula en Java (nunca con LOWER()/CONCAT() alrededor de un parametro bind) para que una
   * busqueda nula no deje que Postgres infiera bytea para el parametro y reviente la consulta.
   */
  private static String toLikePattern(String search) {
    if (search == null) {
      return null;
    }
    return "%" + escapeLike(search.toLowerCase(Locale.ROOT)) + "%";
  }

  /** Escapa los wildcards del LIKE. */
  private static String escapeLike(String value) {
    return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  /** Id exacto de acta, cuando el termino de busqueda es un numero. */
  private static Long parseSearchId(String search) {
    if (search == null) {
      return null;
    }
    try {
      return Long.valueOf(search);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /**
   * Estado nombrado por el termino de busqueda (sin distincion de mayusculas; {@code -} y espacios
   * se normalizan a {@code _}). {@code null} cuando el termino no nombra ningun estado.
   */
  static EstadoActa parseEstadoSearch(String search) {
    if (search == null || search.isBlank()) {
      return null;
    }
    String normalized = search.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    try {
      return EstadoActa.valueOf(normalized);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
