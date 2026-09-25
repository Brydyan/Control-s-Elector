package ec.twintailcs.electoral.evidencia.domain;

/**
 * Categorías de observación que un OBSERVER puede marcar sobre su evidencia.
 *
 * <p>Set inicial aprobado por el usuario (2026-09-25, conversación Mavis↔usuario). Son los
 * placeholders que el task doc proponía como ejemplos pedagógicos — el usuario los aceptó
 * explícitamente como set inicial, con la advertencia de que se ampliará más adelante. Ver
 * {@code openspec/changes/back/2026-09-07-t-be-16-observaciones-evidencia/apply-progress.md}.
 *
 * <p>Los valores se persisten como texto en la BD y viajan al frontend. Cambiar este enum es
 * una migración de datos — agregar valores es aditivo (sin migración), renombrar o quitar
 * rompe filas existentes.
 */
public enum TipoObservacion {
  FOTO_BORROSA,
  DATOS_NO_COINCIDEN,
  FIRMA_FALTANTE,
  NUMERO_INCORRECTO,
  OTRA
}
