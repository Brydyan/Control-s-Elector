package ec.twintailcs.electoral.acta.web;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import ec.twintailcs.electoral.auth.application.JwtService;
import ec.twintailcs.electoral.auth.application.PasswordHasher;
import ec.twintailcs.electoral.auth.domain.Rol;
import ec.twintailcs.electoral.auth.domain.RolPermiso;
import ec.twintailcs.electoral.auth.domain.Usuario;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Verifica que el catalogo de JRVs se limite por asignacion cuando consulta un OBSERVER. */
@QuarkusTest
@TestProfile(ObserverCatalogoTestProfile.class)
@DisplayName("JRVs visibles por rol y asignacion")
class ObserverJrvsFiltradasIT {

  private static final String RUTA = "/api/v1/observer/jrvs";

  @Inject PasswordHasher hasher;
  @Inject JwtService jwt;
  @Inject EntityManager em;

  private Long observerAsignadoId;
  private Long observerSinAsignacionesId;
  private Long coordinatorId;
  private Long adminId;
  private Long recintoDirectoId;
  private Long recintoGrupoId;
  private Long recintoNoAsignadoId;
  private Long jrvDirectaId;
  private Long jrvPorRecintoId;
  private Long jrvNoAsignadaId;
  private String tokenObserverAsignado;
  private String tokenObserverSinAsignaciones;
  private String tokenCoordinator;
  private String tokenAdmin;

  @BeforeEach
  void preparar() {
    observerAsignadoId = crearUsuario("OBSERVER");
    observerSinAsignacionesId = crearUsuario("OBSERVER");
    coordinatorId = crearUsuario("COORDINATOR");
    adminId = crearUsuario("ADMIN");
    tokenObserverAsignado = tokenDe(observerAsignadoId, "OBSERVER");
    tokenObserverSinAsignaciones = tokenDe(observerSinAsignacionesId, "OBSERVER");
    tokenCoordinator = tokenDe(coordinatorId, "COORDINATOR");
    tokenAdmin = tokenDe(adminId, "ADMIN");
    armarDataset();
  }

  @AfterEach
  void limpiar() {
    QuarkusTransaction.requiringNew()
        .run(
            () -> {
              em.createNativeQuery(
                      "delete from observer_jrv_asignacion where usuario_id in (:observer1, :observer2)")
                  .setParameter("observer1", observerAsignadoId)
                  .setParameter("observer2", observerSinAsignacionesId)
                  .executeUpdate();
              em.createNativeQuery(
                      "delete from usuario_recinto where usuario_id in (:observer1, :observer2, :coordinator, :admin)")
                  .setParameter("observer1", observerAsignadoId)
                  .setParameter("observer2", observerSinAsignacionesId)
                  .setParameter("coordinator", coordinatorId)
                  .setParameter("admin", adminId)
                  .executeUpdate();
              em.createNativeQuery(
                      "delete from jrv where id in (:directa, :porRecinto, :noAsignada)")
                  .setParameter("directa", jrvDirectaId)
                  .setParameter("porRecinto", jrvPorRecintoId)
                  .setParameter("noAsignada", jrvNoAsignadaId)
                  .executeUpdate();
              em.createNativeQuery(
                      "delete from recinto where id in (:directo, :grupo, :noAsignado)")
                  .setParameter("directo", recintoDirectoId)
                  .setParameter("grupo", recintoGrupoId)
                  .setParameter("noAsignado", recintoNoAsignadoId)
                  .executeUpdate();
              em.createNativeQuery(
                      "delete from usuario where id in (:observer1, :observer2, :coordinator, :admin)")
                  .setParameter("observer1", observerAsignadoId)
                  .setParameter("observer2", observerSinAsignacionesId)
                  .setParameter("coordinator", coordinatorId)
                  .setParameter("admin", adminId)
                  .executeUpdate();
            });
  }

  @Test
  @DisplayName("un OBSERVER recibe solo sus JRVs asignadas directamente o por recinto")
  void observerRecibeSoloJrvsAsignadas() {
    List<Integer> ids = listar(tokenObserverAsignado);

    assertThat(ids)
        .containsExactlyInAnyOrder(jrvDirectaId.intValue(), jrvPorRecintoId.intValue())
        .doesNotContain(jrvNoAsignadaId.intValue());
  }

  @Test
  @DisplayName("un OBSERVER sin asignaciones recibe una lista vacia")
  void observerSinAsignacionesRecibeListaVacia() {
    assertThat(listar(tokenObserverSinAsignaciones)).isEmpty();
  }

  @Test
  @DisplayName("COORDINATOR y ADMIN conservan acceso al catalogo completo")
  void otrosRolesRecibenTodasLasJrvs() {
    int totalJrvs =
        QuarkusTransaction.requiringNew()
            .call(
                () ->
                    ((Number) em.createNativeQuery("select count(*) from jrv").getSingleResult())
                        .intValue());

    assertThat(listar(tokenCoordinator))
        .hasSize(totalJrvs)
        .contains(jrvDirectaId.intValue(), jrvPorRecintoId.intValue(), jrvNoAsignadaId.intValue());
    assertThat(listar(tokenAdmin))
        .hasSize(totalJrvs)
        .contains(jrvDirectaId.intValue(), jrvPorRecintoId.intValue(), jrvNoAsignadaId.intValue());
  }

  private List<Integer> listar(String token) {
    return given()
        .header("Authorization", "Bearer " + token)
        .when()
        .get(RUTA)
        .then()
        .statusCode(200)
        .extract()
        .jsonPath()
        .getList("id", Integer.class);
  }

  private Long crearUsuario(String nombreRol) {
    return QuarkusTransaction.requiringNew()
        .call(
            () -> {
              Rol rol = Rol.find("nombre", nombreRol).firstResult();
              Usuario usuario = new Usuario();
              usuario.setEmail(nombreRol.toLowerCase() + "-" + UUID.randomUUID() + "@test.local");
              usuario.setNombre(nombreRol);
              usuario.setApellido("Prueba");
              usuario.setPasswordHash(hasher.hash("clave-de-prueba-123"));
              usuario.setRol(rol);
              usuario.setHabilitado(true);
              usuario.persist();
              return usuario.getId();
            });
  }

  private String tokenDe(Long usuarioId, String nombreRol) {
    Usuario usuario = QuarkusTransaction.requiringNew().call(() -> Usuario.findById(usuarioId));
    List<String> permisos =
        QuarkusTransaction.requiringNew()
            .call(() -> RolPermiso.authorityStringsByUsuarioId(usuarioId));
    return jwt.issue(
            usuario.getPublicId(),
            usuario.getEmail(),
            nombreRol,
            permisos,
            UUID.randomUUID().toString())
        .access();
  }

  private void armarDataset() {
    String sufijo = UUID.randomUUID().toString().substring(0, 8);
    QuarkusTransaction.requiringNew()
        .run(
            () -> {
              recintoDirectoId = crearRecinto("Directo", sufijo);
              recintoGrupoId = crearRecinto("Grupo", sufijo);
              recintoNoAsignadoId = crearRecinto("Sin asignar", sufijo);
              jrvDirectaId = crearJrv("Directa", sufijo, recintoDirectoId);
              jrvPorRecintoId = crearJrv("Por recinto", sufijo, recintoGrupoId);
              jrvNoAsignadaId = crearJrv("No asignada", sufijo, recintoNoAsignadoId);

              em.createNativeQuery(
                      "insert into observer_jrv_asignacion (usuario_id, jrv_id)"
                          + " values (:usuario, :jrv)")
                  .setParameter("usuario", observerAsignadoId)
                  .setParameter("jrv", jrvDirectaId)
                  .executeUpdate();
              em.createNativeQuery(
                      "insert into usuario_recinto (usuario_id, recinto_id, rol_en_recinto)"
                          + " values (:usuario, :recinto, 'OBSERVER')")
                  .setParameter("usuario", observerAsignadoId)
                  .setParameter("recinto", recintoGrupoId)
                  .executeUpdate();
            });
  }

  private Long crearRecinto(String etiqueta, String sufijo) {
    return ((Number)
            em.createNativeQuery(
                    "insert into recinto (nombre, parroquia_id)"
                        + " select :nombre, p.id from parroquia p order by p.id limit 1"
                        + " returning id")
                .setParameter("nombre", "Recinto " + etiqueta + " " + sufijo)
                .getSingleResult())
        .longValue();
  }

  private Long crearJrv(String etiqueta, String sufijo, Long recintoId) {
    String numero = "JRV-" + sufijo + "-" + etiqueta.substring(0, 2).toUpperCase();
    return ((Number)
            em.createNativeQuery(
                    "insert into jrv (numero, recinto, total_electores, parroquia_id, recinto_id)"
                        + " select :numero, r.nombre, 100, r.parroquia_id, r.id from recinto r"
                        + " where r.id = :recinto returning id")
                .setParameter("numero", numero)
                .setParameter("recinto", recintoId)
                .getSingleResult())
        .longValue();
  }
}
