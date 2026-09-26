package ec.twintailcs.electoral.incidencia.domain;

/**
 * Tipos de contingencia operativa en una JRV (T-BE-19). No confundir con
 * {@link ec.twintailcs.electoral.evidencia.domain.TipoObservacion}, que es sobre la calidad
 * de una foto del acta.
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
