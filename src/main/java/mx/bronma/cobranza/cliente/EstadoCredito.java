package mx.bronma.cobranza.cliente;

/** Lo decide la oficina, normalmente siguiendo la recomendacion de Ayax. */
public enum EstadoCredito {
    ACTIVO,
    /** Se le bajo el limite. */
    REDUCIDO,
    /** Ya no se le fia: solo contado hasta que se ponga al corriente. */
    SUSPENDIDO
}
