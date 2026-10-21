package mx.bronma.cobranza.recordatorio;

public enum EstadoRecordatorio {
    /** Programado, todavia no llega su dia o no se ha procesado. */
    PENDIENTE,
    ENVIADO,
    /** El proveedor rechazo el envio; ver columna error. */
    FALLIDO,
    /** Ya no aplica: la nota se pago o se cancelo antes de su dia. */
    CANCELADO
}
