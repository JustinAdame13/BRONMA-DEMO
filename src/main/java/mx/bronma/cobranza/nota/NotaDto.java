package mx.bronma.cobranza.nota;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Una cuenta por cobrar, como la ve el frontend.
 *
 * @param vencida     saldo > 0, no cancelada y ya paso su vencimiento (se calcula, no se guarda).
 * @param diasAtraso  0 si no esta vencida. Sirve para la antiguedad de cartera.
 * @param rutaId      la ruta de la venta (foto), no necesariamente la ruta actual del cliente.
 */
public record NotaDto(
        Long id,
        String folioOrigen,
        Long clienteId,
        String clienteCodigo,
        String clienteNombre,
        Long rutaId,
        String rutaNombre,
        LocalDate fechaEmision,
        LocalDate fechaVencimiento,
        BigDecimal monto,
        BigDecimal saldo,
        EstadoNota estado,
        boolean vencida,
        int diasAtraso,
        Long importacionId) {
}
