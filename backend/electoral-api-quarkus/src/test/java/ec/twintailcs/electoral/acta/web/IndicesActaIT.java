package ec.twintailcs.electoral.acta.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T-PERF-OPT-01 E.1: los indices de las actas existen y son los que el SDD pidio.
 *
 * <p>El SDD pedia un test que verificara que las queries del escrutinio usan indices. Verificar eso
 * en un test es fragil: el planner elige segun el volumen y la distribucion de los datos, y un test
 * con la base casi vacia veria seq scan aunque el indice exista y sea el correcto para produccion.
 * Lo que SI es determinista —y lo que de verdad puede romperse sin que nadie se entere— es que la
 * migracion este aplicada y que los indices existan con el nombre y las columnas que el resto del
 * sistema da por hechos.
 *
 * <p>Por eso este test afirma sobre el catalogo de la base ({@code pg_indexes}), y la evidencia de
 * que el planner elige el indice con 50 000 filas esta en {@code documents/INDEXING_GUIDE.md} y en
 * el {@code apply-progress.md} de la SDD.
 */
@QuarkusTest
@TestProfile(IndicesActaTestProfile.class)
@DisplayName("Indices de acta y acta_detalle")
class IndicesActaIT {

  @Inject EntityManager em;

  @Test
  @DisplayName("los 6 indices de las actas existen con las columnas esperadas")
  void losIndicesDeLasActasExisten() {
    List<String> definiciones = indicesDe("acta");

    assertThat(definiciones)
        .as("idx_acta_registrada_por sobre (registrada_por)")
        .anyMatch(
            d -> d.contains("INDEX idx_acta_registrada_por ") && d.contains("(registrada_por)"));
    assertThat(definiciones)
        .as("idx_acta_jrv_id sobre (jrv_id)")
        .anyMatch(d -> d.contains("INDEX idx_acta_jrv_id ") && d.contains("(jrv_id)"));
    assertThat(definiciones)
        .as("idx_acta_cargo_id sobre (cargo_id)")
        .anyMatch(d -> d.contains("INDEX idx_acta_cargo_id ") && d.contains("(cargo_id)"));
    assertThat(definiciones)
        .as("idx_acta_fecha_registro_desc sobre (fecha_registro DESC)")
        .anyMatch(
            d ->
                d.contains("INDEX idx_acta_fecha_registro_desc ")
                    && d.contains("fecha_registro DESC"));
    assertThat(definiciones)
        .as("idx_acta_jrv_cargo_estado sobre (jrv_id, cargo_id, estado)")
        .anyMatch(
            d ->
                d.contains("INDEX idx_acta_jrv_cargo_estado ")
                    && d.contains("(jrv_id, cargo_id, estado)"));
    assertThat(definiciones)
        .as("idx_acta_estado sobre (estado)")
        .anyMatch(d -> d.contains("INDEX idx_acta_estado ") && d.contains("(estado)"));
  }

  @Test
  @DisplayName("el indice de acta_detalle por candidato existe")
  void elIndiceDeActaDetalleExiste() {
    List<String> definiciones = indicesDe("acta_detalle");

    assertThat(definiciones)
        .as("idx_acta_detalle_candidato_id sobre (candidato_id)")
        .anyMatch(
            d ->
                d.contains("INDEX idx_acta_detalle_candidato_id ") && d.contains("(candidato_id)"));
  }

  /** Definiciones de los indices de una tabla, tal como las reporta Postgres. */
  private List<String> indicesDe(String tabla) {
    @SuppressWarnings("unchecked")
    List<Object> filas =
        em.createNativeQuery(
                "select indexdef from pg_indexes where schemaname = 'public' and tablename = :tabla"
                    + " order by indexname")
            .setParameter("tabla", tabla)
            .getResultList();
    return filas.stream().map(fila -> (String) fila).toList();
  }
}
