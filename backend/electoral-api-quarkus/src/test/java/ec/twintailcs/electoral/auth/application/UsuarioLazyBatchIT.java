package ec.twintailcs.electoral.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import ec.twintailcs.electoral.auth.domain.Rol;
import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.auth.web.dto.UsuarioResponseDto;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import java.util.List;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T-PERF-OPT-01 B.1: el cambio a LAZY no introduce N+1.
 *
 * <p>El SDD de performance cambia {@code Usuario.rol} de EAGER a LAZY y promete dos cosas: que la
 * pagina de administracion (20 usuarios) no se convierta en una consulta por usuario, y que ningun
 * consumidor quede fuera de la transaccion con la sesion cerrada (que es cuando el LAZY revienta
 * con {@code LazyInitializationException}).
 *
 * <p>Las dos se comprueban aqui:
 *
 * <ul>
 *   <li><b>Sin N+1:</b> se cuentan las sentencias SQL que ejecuta el listado. Se listan usuarios de
 *       <em>roles distintos</em> y se afirma que el numero de sentencias no crece con el numero de
 *       usuarios: con {@code @BatchSize(25)} sobre {@code Rol} los roles de la pagina salen en una
 *       sola consulta extra (un {@code IN}), no en una por rol.
 *   <li><b>Sin LazyInitializationException:</b> el listado devuelve el nombre del rol de cada
 *       usuario, que es exactamente el acceso que dispara la carga diferida. Si el mapper leyera el
 *       rol fuera de la transaccion, el test reventaria con la excepcion.
 * </ul>
 *
 * <p>El conteo es de <em>sentencias</em> ({@code getPrepareStatementCount}), no de consultas HQL
 * ({@code getQueryExecutionCount}): el segundo solo ve el JPQL de la aplicacion y seeria el mismo
 * con la asociacion EAGER o LAZY. Lo que distingue a un N+1 es el SQL que Hibernate emite por
 * detras.
 *
 * <p>El test arma sus propios usuarios, con roles distintos: el reparto de roles del seed cambia
 * con cada ejecucion y un test que depende de el no seria fiable.
 */
@QuarkusTest
@TestProfile(UsuarioLazyBatchTestProfile.class)
@DisplayName("Usuario.rol LAZY sin N+1")
class UsuarioLazyBatchIT {

  @Inject UsuarioAdminService usuarioAdminService;
  @Inject EntityManagerFactory emf;
  @Inject EntityManager em;

  /** Ids de los usuarios creados por el test, para borrarlos al terminar. */
  private List<Long> idsCreados;

  @BeforeEach
  void crearUsuariosConRolesDistintos() {
    idsCreados =
        QuarkusTransaction.requiringNew()
            .call(
                () -> {
                  List<Long> ids = new java.util.ArrayList<>();
                  // Tres usuarios, tres roles distintos: la pagina del listado tiene que resolver
                  // tres roles diferentes, y con EAGER o con LAZY sin lote eso son tres consultas.
                  for (String nombreRol : List.of("MASTER", "ADMIN", "COORDINATOR")) {
                    Usuario u = new Usuario();
                    u.setEmail("lazy-batch-" + UUID.randomUUID() + "@test.local");
                    u.setNombre("Lazy");
                    u.setApellido("Batch");
                    u.setPasswordHash("x".repeat(60));
                    u.setRol(Rol.find("nombre", nombreRol).firstResult());
                    u.setHabilitado(true);
                    u.persist();
                    ids.add(u.getId());
                  }
                  return ids;
                });

    // El contador se enciende y se limpia en cada test: asi los deltas son del test, no de la
    // sesion.
    stats().setStatisticsEnabled(true);
    stats().clear();
  }

  @AfterEach
  void borrarUsuarios() {
    List<Long> ids = idsCreados;
    QuarkusTransaction.requiringNew()
        .run(
            () -> {
              for (Long id : ids) {
                em.createNativeQuery("delete from usuario where id = :id")
                    .setParameter("id", id)
                    .executeUpdate();
              }
            });
  }

  @Test
  @DisplayName("resolver los roles de 3 usuarios no cuesta mas que resolver los de 1")
  void resolverLosRolesNoEscalaConElNumeroDeUsuarios() {
    long sentenciasDeUno = contarSentencias(1);
    long sentenciasDeTres = contarSentencias(3);

    assertThat(sentenciasDeTres)
        .as(
            "listar 3 usuarios de roles distintos cuesta %s sentencias y listar 1 cuesta %s: el"
                + " numero de sentencias tiene que ser el mismo (los roles salen en una consulta"
                + " con IN, no en una por usuario)",
            sentenciasDeTres, sentenciasDeUno)
        .isEqualTo(sentenciasDeUno);
  }

  @Test
  @DisplayName("el listado devuelve el nombre del rol de cada usuario")
  void elListadoDevuelveElRolDeCadaUsuario() {
    List<UsuarioResponseDto> usuarios =
        usuarioAdminService.listar(0, 20, "creadoEn", null, null).data();

    List<UsuarioResponseDto> delTest =
        usuarios.stream().filter(u -> u.email().startsWith("lazy-batch-")).toList();

    assertThat(delTest).as("los tres usuarios del test aparecen en el listado").hasSize(3);
    assertThat(delTest)
        .as("cada usuario trae el nombre de su rol: ese es el acceso que dispara la carga LAZY")
        .allSatisfy(
            usuario ->
                assertThat(usuario.rolNombre())
                    .as("el rol del usuario %s", usuario.email())
                    .isNotBlank());
  }

  @Test
  @DisplayName("la asociacion a Rol esta mapeada como LAZY, como pide el SDD")
  void laAsociacionARolEsLazy() throws NoSuchFieldException {
    ManyToOne anotacion = Usuario.class.getDeclaredField("rol").getAnnotation(ManyToOne.class);

    assertThat(anotacion).as("el campo 'rol' esta anotado con @ManyToOne").isNotNull();
    assertThat(anotacion.fetch())
        .as("T-PERF-OPT-01 B.1: la asociacion se declara LAZY")
        .isEqualTo(FetchType.LAZY);
  }

  /** Numero de sentencias SQL que cuesta listar una pagina del tamano dado. */
  private long contarSentencias(int tamanoPagina) {
    stats().clear();
    usuarioAdminService.listar(0, tamanoPagina, "creadoEn", null, null);
    return stats().getPrepareStatementCount();
  }

  /** Estadisticas de Hibernate de la aplicacion. */
  private Statistics stats() {
    return emf.unwrap(SessionFactory.class).getStatistics();
  }
}
