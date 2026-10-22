package mx.bronma.cobranza.cobro;

/** Como ordenar la lista de cobro. */
public enum OrdenLista {
    /** El mas urgente primero (default). */
    PRIORIDAD,
    /** En el orden de visita de la ruta; los que no tienen orden al final, por prioridad. */
    VISITA
}
