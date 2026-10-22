package ec.twintailcs.electoral.evidencia.domain;

/**
 * Categorias de observacion que un OBSERVER puede marcar sobre su evidencia.
 *
 * <p>Set inicial aprobado por el usuario (2026-09-25). Los valores se persisten como texto en la
 * BD y viajan al frontend. Agregar valores es aditivo; renombrar o quitar rompe filas existentes.
 */
public enum TipoObservacion {
  FOTO_BORROSA,
  DATOS_NO_COINCIDEN,
  FIRMA_FALTANTE,
  NUMERO_INCORRECTO,
  OTRA
}
