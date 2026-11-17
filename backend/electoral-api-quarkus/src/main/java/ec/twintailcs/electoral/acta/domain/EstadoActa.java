package ec.twintailcs.electoral.acta.domain;

/**
 * Estados posibles de un acta electoral.
 *
 * <p>La maquina de estados es: BORRADOR → ENVIADA → EN_DISPUTA → CORREGIDA → APROBADA → ANULADA.
 * ANULADA es terminal e irreversible.
 */
public enum EstadoActa {
  BORRADOR, // guardado pero no enviado
  ENVIADA, // enviado para revision
  EN_DISPUTA, // marcado con problema por COORDINATOR
  CORREGIDA, // corregida por OBSERVER o COORDINATOR
  APROBADA, // aprobada por COORDINATOR
  ANULADA // anulada por MASTER — estado final, irreversible
}
