package ec.twintailcs.electoral.auth.application;

import ec.twintailcs.electoral.auth.domain.EstadoUsuario;
import ec.twintailcs.electoral.auth.domain.RecursoNoEncontradoException;
import ec.twintailcs.electoral.auth.domain.Rol;
import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.auth.web.dto.CreateUsuarioRequest;
import ec.twintailcs.electoral.auth.web.dto.UpdateUsuarioRequest;
import ec.twintailcs.electoral.auth.web.dto.UsuarioResponseDto;
import ec.twintailcs.electoral.auth.web.dto.mapper.UsuarioMapper;
import ec.twintailcs.electoral.shared.web.pagination.PageSpec;
import ec.twintailcs.electoral.shared.web.pagination.PagedResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Administracion de usuarios: listar, ver, crear, actualizar y deshabilitar.
 *
 * <p>Es el servicio que lleva el requisito de permisos de cada operacion ({@link Requiere}). El
 * recurso HTTP solo arma la peticion y devuelve la respuesta: la logica y la autorizacion viven
 * aca.
 *
 * <p>Por que el requisito esta en el servicio y no en el recurso: Quarkus REST invoca los metodos
 * del recurso directamente y NO les aplica los interceptores CDI, asi que una anotacion
 * {@code @Requiere} en el recurso no se evaluaria. En un bean CDI el interceptor si corre, y como
 * el recurso llama al servicio DESPUES de que el body fue validado, el orden es el mismo que en
 * Spring: primero la validacion (400) y despues la autorizacion (403).
 */
@ApplicationScoped
public class UsuarioAdminService {

  private static final Set<String> SORTABLE_FIELDS =
      Set.of("creadoEn", "email", "apellido", "nombre");

  private static final SecureRandom RANDOM = new SecureRandom();

  @Inject UsuarioMapper mapper;

  @Inject EmailGenerator emailGenerator;

  @Inject PasswordHasher passwordHasher;

  @Inject InvitacionService invitacionService;

  /** Listar usuarios, con filtros opcionales. */
  @Transactional
  public PagedResponse<UsuarioResponseDto> listar(
      int page, int size, String sort, String rol, Boolean habilitado) {
    PageSpec spec = PageSpec.from(page, size, sort, SORTABLE_FIELDS, "creadoEn");

    Map<String, Object> params = new LinkedHashMap<>();
    StringBuilder where = new StringBuilder();
    if (rol != null) {
      where.append(" and u.rol.nombre = :rol");
      params.put("rol", rol);
    }
    if (habilitado != null) {
      where.append(" and u.habilitado = :habilitado");
      params.put("habilitado", habilitado);
    }
    String condicion = where.length() == 0 ? "" : " where " + where.substring(5);

    EntityManager em = Usuario.getEntityManager();
    String jpql =
        "select u from Usuario u"
            + condicion
            + " order by u."
            + spec.campo()
            + (spec.ascendente() ? " asc" : " desc")
            + ", u.id asc";
    TypedQuery<Usuario> consulta = em.createQuery(jpql, Usuario.class);
    params.forEach(consulta::setParameter);
    consulta.setFirstResult(spec.page() * spec.size());
    consulta.setMaxResults(spec.size());
    List<Usuario> usuarios = consulta.getResultList();

    TypedQuery<Long> conteo =
        em.createQuery("select count(u) from Usuario u" + condicion, Long.class);
    params.forEach(conteo::setParameter);
    long total = conteo.getSingleResult();

    return PagedResponse.of(
        usuarios.stream().map(mapper::toAdminDto).toList(), spec.page(), spec.size(), total);
  }

  /** Obtener usuario por su UUID publico. */
  @Transactional
  public UsuarioResponseDto obtener(UUID publicId) {
    return mapper.toAdminDto(findUser(publicId));
  }

  /**
   * Crear usuario con password temporal autogenerado. El email se genera solo.
   *
   * <p>Con {@code sendInvitation=true} la cuenta nace en estado {@code PENDIENTE_INVITACION} y
   * ademas se manda la invitacion por email: el usuario elige su propia contrasena y la temporal
   * queda desactivada por el estado de la cuenta. Con {@code sendInvitation=false} el comportamiento
   * es el de siempre: la cuenta nace activa y el panel devuelve la contrasena temporal para
   * comunicarla.
   *
   * <p>En los dos casos se devuelve la contrasena temporal, y en el caso de la invitacion sirve
   * para una cosa concreta: si el usuario la prueba, el login responde 403 con "revisá tu email" en
   * vez de "credenciales inválidas". Con credenciales correctas y la cuenta todavia pendiente, el
   * mensaje util es el que dice que falta activar; uno de credenciales inválidas lo manda a buscar
   * una contrasena que si existe.
   *
   * <p>La distincion entre los dos flujos es explicita a proposito: el default es el comportamiento
   * anterior (sin invitacion), y el flujo por email se pide a mano. Un default que mandara
   * invitaciones cambiaria el flujo de altas que ya usa el MASTER en cada eleccion.
   *
   * <p>Acepta {@code sendInvitation} como parametro principal. Si no viene, usa {@code invitar}
   * por compatibilidad con clientes antiguos.
   */
  @Transactional
  public UsuarioResponseDto crear(CreateUsuarioRequest request) {
    // sendInvitation es el parametro nuevo; invitar se mantiene por compatibilidad
    boolean enviarInvitacion = request.sendInvitation() || request.invitar();

    Rol rol = findRole(request.rolNombre());
    String password = temporaryPassword();
    Usuario usuario = new Usuario();
    usuario.setNombre(request.nombre());
    usuario.setApellido(request.apellido());
    usuario.setEmail(
        emailGenerator.generar(request.nombre(), request.apellido(), request.rolNombre()));
    usuario.setPasswordHash(passwordHasher.hash(password));
    usuario.setRol(rol);
    usuario.setHabilitado(true);
    if (enviarInvitacion) {
      usuario.setEstadoUsuario(EstadoUsuario.PENDIENTE_INVITACION);
    }
    usuario.validarDatosObligatorios();
    usuario.persist();

    if (enviarInvitacion) {
      // La invitacion se genera con el mismo camino que el envio en lote: un solo
      // lugar que emite el token, guarda el hash y encola el email.
      invitacionService.enviarInvitaciones(List.of(usuario.getPublicId()));
    }

    UsuarioResponseDto response = mapper.toAdminDto(usuario);
    return new UsuarioResponseDto(
        response.publicId(),
        response.email(),
        response.nombre(),
        response.apellido(),
        response.rolNombre(),
        response.habilitado(),
        response.creadoEn(),
        password,
        response.estadoUsuario(),
        response.invitationSentAt(),
        response.invitationStatus(),
        response.invitationExpiresAt());
  }

  /** Actualizar nombre, apellido, rol y estado de habilitacion. */
  @Transactional
  public UsuarioResponseDto actualizar(UUID publicId, UpdateUsuarioRequest request) {
    Usuario usuario = findUser(publicId);
    usuario.setNombre(request.nombre());
    usuario.setApellido(request.apellido());
    usuario.setRol(findRole(request.rolNombre()));
    usuario.setHabilitado(request.habilitado());
    return mapper.toAdminDto(usuario);
  }

  /** Deshabilitar usuario (soft-delete). */
  @Transactional
  public void deshabilitar(UUID publicId) {
    Usuario usuario = findUser(publicId);
    usuario.setHabilitado(false);
  }

  // --------- helpers ---------

  private Usuario findUser(UUID publicId) {
    Usuario usuario = Usuario.find("publicId", publicId).firstResult();
    if (usuario == null) {
      throw new RecursoNoEncontradoException("Usuario no encontrado");
    }
    return usuario;
  }

  private Rol findRole(String nombre) {
    Rol rol = Rol.find("nombre", nombre).firstResult();
    if (rol == null) {
      throw new IllegalArgumentException("rol invalido: " + nombre);
    }
    return rol;
  }

  private static String temporaryPassword() {
    byte[] bytes = new byte[18];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
