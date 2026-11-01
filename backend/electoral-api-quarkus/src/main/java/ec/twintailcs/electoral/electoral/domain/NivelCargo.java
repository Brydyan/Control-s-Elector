package ec.twintailcs.electoral.electoral.domain;

/**
 * Nivel geografico de un cargo electoral.
 *
 * <p>Mismos valores que el enum del backend Spring: se guardan como texto en la columna {@code
 * nivel} del schema.
 */
public enum NivelCargo {
  NACIONAL, // Consejero CPCCS — aplica en TODAS las JRVs del pais
  PROVINCIA, // Prefecto/a — aplica en todas las JRVs de la provincia
  CANTON, // Alcalde, Concejal Urbano/Rural — aplica en JRVs del canton
  PARROQUIA // Vocal Junta Parroquial — solo en parroquias rurales
}
