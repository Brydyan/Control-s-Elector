package ec.twintailcs.electoral.acta.domain;

public enum EstadoActa {
    BORRADOR,    // guardado pero no enviado
    ENVIADA,     // enviado para revisión
    EN_DISPUTA,  // marcado con problema por COORDINATOR
    CORREGIDA,   // corregida por OBSERVER o COORDINATOR
    APROBADA,    // aprobada por COORDINATOR
    ANULADA      // anulada por MASTER — estado final, irreversible
}
