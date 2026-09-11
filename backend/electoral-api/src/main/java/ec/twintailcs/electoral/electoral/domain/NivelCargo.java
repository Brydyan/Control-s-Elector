package ec.twintailcs.electoral.electoral.domain;

public enum NivelCargo {
  NACIONAL, // Consejero CPCCS — aplica en TODAS las JRVs del país
  PROVINCIA, // Prefecto/a — aplica en todas las JRVs de la provincia
  CANTON, // Alcalde, Concejal Urbano/Rural — aplica en JRVs del cantón
  PARROQUIA // Vocal Junta Parroquial — solo en parroquias rurales
}
