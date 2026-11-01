package ec.twintailcs.electoral.acta.incidencia.domain;

/**
 * Tipos de contingencia operativa en una JRV (T-BE-19).
 *
 * <p>No confundir con el enum de calidad de fotos del modulo de evidencias: este es sobre lo que
 * pasa en la mesa el dia de la votacion.
 */
public enum TipoIncidenciaJrv {
  JRV_NO_INSTALADA,
  IMPEDIMENTO_VEEDOR,
  URNA_VIOLENTADA,
  FALTA_PAPELETAS,
  CORTE_ENERGIA,
  DISTURBIOS_RECINTO,
  OTRO
}
