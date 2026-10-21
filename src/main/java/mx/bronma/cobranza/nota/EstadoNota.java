package mx.bronma.cobranza.nota;

/**
 * No hay VENCIDA a proposito: vencida = saldo > 0 y fecha_vencimiento < hoy.
 * Es un dato que cambia solo con el paso del tiempo, asi que se calcula.
 * La tabla tiene un CHECK para que el estado siempre cuadre con el saldo.
 */
public enum EstadoNota {
    /** saldo = monto (no ha recibido abonos). */
    ABIERTA,
    /** 0 < saldo < monto. */
    PARCIAL,
    /** saldo = 0. */
    PAGADA,
    /** Anulada en el sistema de ventas; no cuenta en la cartera. */
    CANCELADA
}
