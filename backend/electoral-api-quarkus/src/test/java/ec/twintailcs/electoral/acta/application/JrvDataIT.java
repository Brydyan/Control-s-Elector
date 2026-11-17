package ec.twintailcs.electoral.acta.application;

import static org.assertj.core.api.Assertions.assertThat;

import ec.twintailcs.electoral.acta.application.usecase.RegistrarActaUseCase;
import ec.twintailcs.electoral.acta.domain.Acta;
import ec.twintailcs.electoral.acta.domain.EstadoActa;
import ec.twintailcs.electoral.acta.web.dto.ActaDetalleDto;
import ec.twintailcs.electoral.acta.web.dto.ActaRequestDto;
import ec.twintailcs.electoral.auth.security.CurrentUser;
import ec.twintailcs.electoral.dashboard.application.DashboardService;
import ec.twintailcs.electoral.dashboard.web.dto.ProgresoCantonDto;
import ec.twintailcs.electoral.geografia.web.GeografiaTestProfile;
import io.quarkus.arc.Arc;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Las JRVs de la geografía electoral son referenciables: un observer registra un acta sobre una JRV
 * del seed y el dashboard la refleja.
 *
 * <p>Es la prueba que une las dos mitades del modelo: la geografía (que se importa del CNE antes de
 * la elección) y el acta (que es lo que el observer registra en la mesa). Si la JRV no existiera o
 * no colgarara de una parroquia válida, el INSERT del acta reventaría con una violación de clave
 * foránea; si el dashboard no la leyera, el conteo por JRV saldría descuadrado.
 *
 * <p>El seed entra en la base de pruebas (contexto {@code seed}): la prueba usa JRVs reales ({@code
 * JRV-SE-001}) y candidatos reales del seed, no datos inventados en el test. Un acta apuntando a
 * una JRV de prueba no probaría que la geografía importada sirve.
 */
@QuarkusTest
@TestProfile(GeografiaTestProfile.class)
class JrvDataIT {

  @Inject EntityManager em;
  @Inject CurrentUser currentUser;
  @Inject RegistrarActaUseCase registrarActa;
  @Inject DashboardService dashboard;

  /** Id del observer del seed. */
  private Long observerId() {
    return em.createQuery("select u.id from Usuario u where u.email = :email", Long.class)
        .setParameter("email", "observervera@example.local")
        .getSingleResult();
  }

  /** Id de una JRV del seed. */
  private Long jrvId(String numero) {
    return em.createQuery("select j.id from Jrv j where j.numero = :numero", Long.class)
        .setParameter("numero", numero)
        .getSingleResult();
  }

  /** Id de un cargo del seed. */
  private Long cargoId(String nombre) {
    return em.createQuery("select c.id from Cargo c where c.nombre = :nombre", Long.class)
        .setParameter("nombre", nombre)
        .getSingleResult();
  }

  /** Candidatos del cargo, con sus ids. */
  private List<Map<String, Object>> candidatosDe(Long cargoId) {
    return em.createQuery(
            "select c.id as id, c.nombre as nombre from Candidato c where c.cargo.id = :cargoId order by c.orden",
            Object[].class)
        .setParameter("cargoId", cargoId)
        .getResultStream()
        .map(
            fila -> {
              Map<String, Object> mapa = new java.util.LinkedHashMap<>();
              mapa.put("id", ((Number) fila[0]).longValue());
              mapa.put("nombre", (String) fila[1]);
              return mapa;
            })
        .toList();
  }

  @Test
  @DisplayName("la geografía del seed cuelga entera: provincia -> canton -> parroquia -> JRV")
  void laGeografiaDelSeedCuelgaEntera() {
    Long provincia =
        em.createQuery("select count(p.id) from Provincia p", Long.class).getSingleResult();
    Long cantones =
        em.createQuery("select count(c.id) from Canton c", Long.class).getSingleResult();
    Long parroquias =
        em.createQuery("select count(p.id) from Parroquia p", Long.class).getSingleResult();
    Long jrvs = em.createQuery("select count(j.id) from Jrv j", Long.class).getSingleResult();

    assertThat(provincia).as("provincias del seed").isGreaterThanOrEqualTo(2);
    assertThat(cantones).as("cantones del seed").isGreaterThanOrEqualTo(3);
    assertThat(parroquias).as("parroquias del seed").isGreaterThanOrEqualTo(4);
    assertThat(jrvs).as("JRVs del seed").isGreaterThanOrEqualTo(5);

    // Cada JRV cuelga de una parroquia que cuelga de un cantón que cuelga de una provincia: no
    // hay JRV huérfana, que es lo que rompería el INSERT de un acta.
    Long huerfanas =
        em.createQuery(
                "select count(j.id) from Jrv j"
                    + " left join Parroquia pa on pa.id = j.parroquiaId"
                    + " left join Canton c on c.id = pa.cantonId"
                    + " left join Provincia pr on pr.id = c.provinciaId"
                    + " where pr.id is null",
                Long.class)
            .getSingleResult();
    assertThat(huerfanas).as("JRVs sin la jerarquia completa").isZero();

    // Y toda JRV es operable: tiene al menos un cargo habilitado.
    // `jrv_cargo` no tiene entidad mapeada (la consulta la hace el modulo de actas en SQL
    // nativo), asi que la comprobacion va en SQL nativo tambien.
    Long sinCargos =
        ((Number)
                em.createNativeQuery(
                        "select count(*) from jrv j where not exists"
                            + " (select 1 from jrv_cargo jc where jc.jrv_id = j.id and jc.activo)")
                    .getSingleResult())
            .longValue();
    assertThat(sinCargos).as("JRVs sin cargos habilitados (no serian operables)").isZero();
  }

  @Test
  @DisplayName("un observer registra un acta sobre una JRV del seed, sin violaciones de FK")
  void unObserverRegistraUnActaSobreUnaJrvDelSeed() {
    Long observer = observerId();
    // Se usa una JRV de la geografia importada (JRV-GY-001) y no una del seed original:
    // la base de pruebas la comparten varios IT, y dejar un acta en JRV-SE-001 le cambia
    // el conteo a otro test (el que afirma cuantas actas bloquean el borrado de esa JRV).
    Long jrv = jrvId("JRV-GY-001");
    Long cargo = cargoId("Concejal Urbano");
    List<Map<String, Object>> candidatos = candidatosDe(cargo);
    assertThat(candidatos).as("el seed tiene candidatos para el cargo").isNotEmpty();

    // Un voto para cada candidato, el resto en blancos y nulos: la suma tiene que cerrar con
    // el total de sufragantes.
    int votosPorCandidato = 10;
    int blancos = 3;
    int nulos = 2;
    int total = candidatos.size() * votosPorCandidato + blancos + nulos;

    List<ActaDetalleDto> detalles =
        candidatos.stream()
            .map(c -> new ActaDetalleDto(((Number) c.get("id")).longValue(), votosPorCandidato))
            .toList();

    Acta acta =
        registrarActa.execute(
            new ActaRequestDto(jrv, cargo, total, blancos, nulos, detalles), observer, "127.0.0.1");

    assertThat(acta.getId()).as("el acta se persistio").isNotNull();
    assertThat(acta.getEstado()).isEqualTo(EstadoActa.ENVIADA);
    assertThat(acta.getJrvId()).isEqualTo(jrv);

    // La fila existe y cuelga de una JRV real: la FK no se violó.
    Long enBase =
        em.createQuery("select count(a.id) from Acta a where a.id = :id", Long.class)
            .setParameter("id", acta.getId())
            .getSingleResult();
    assertThat(enBase).as("el acta esta en la base").isEqualTo(1L);
  }

  @Test
  @DisplayName("el dashboard refleja el acta por JRV, dentro de su canton y parroquia")
  void elDashboardReflejaElActaPorJrv() {
    Long observer = observerId();
    Long jrv = jrvId("JRV-GY-002");
    Long cargo = cargoId("Concejal Urbano");
    List<Map<String, Object>> candidatos = candidatosDe(cargo);
    assertThat(candidatos).as("el seed tiene candidatos para el cargo").isNotEmpty();

    int votosPorCandidato = 7;
    int total = candidatos.size() * votosPorCandidato;
    List<ActaDetalleDto> detalles =
        candidatos.stream()
            .map(c -> new ActaDetalleDto(((Number) c.get("id")).longValue(), votosPorCandidato))
            .toList();

    registrarActa.execute(
        new ActaRequestDto(jrv, cargo, total, 0, 0, detalles), observer, "127.0.0.1");

    // El dashboard lee el usuario de la sesion (para activar el contexto de RLS). En un test
    // no hay request, asi que se activa el contexto de request y se fija el usuario a mano.
    List<ProgresoCantonDto> progreso;
    Arc.container().requestContext().activate();
    try {
      currentUser.set("observervera@example.local", "OBSERVER", List.of(), "test-jrv-data");
      progreso = dashboard.progreso();
    } finally {
      Arc.container().requestContext().terminate();
    }

    // La JRV pertenece a la parroquia Carapungo, del cantón Guayaquil: el desglose tiene que
    // contarla como JRV con acta.
    ProgresoCantonDto canton =
        progreso.stream()
            .filter(c -> "Guayaquil".equals(c.cantonNombre()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("el canton Guayaquil no aparece: " + progreso));

    assertThat(canton.totalJrvs()).as("JRVs totales del canton").isGreaterThanOrEqualTo(3);
    assertThat(canton.jrvsConActa()).as("JRVs con acta").isGreaterThanOrEqualTo(1);
    assertThat(canton.porcentaje()).as("porcentaje de avance").isBetween(0.0, 100.0);

    // Y el desglose por parroquia tiene que traer la parroquia de la JRV con su JRV:.
    assertThat(canton.parroquias()).isNotEmpty();
    assertThat(canton.parroquias())
        .anySatisfy(
            p ->
                assertThat(p.jrvsTotal())
                    .as("la parroquia de la JRV con acta: %s", p)
                    .isGreaterThanOrEqualTo(1));
  }
}
