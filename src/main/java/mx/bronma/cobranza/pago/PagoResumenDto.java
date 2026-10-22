package mx.bronma.cobranza.pago;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Cada elemento de GET /pagos.
 *
 * @param saldoAFavor lo que de este pago sigue sin aplicar a ninguna nota.
 */
public record PagoResumenDto(
        Long id,
        Long clienteId,
        String clienteNombre,
        Long rutaId,
        BigDecimal monto,
        Instant fechaPago,
        MetodoPago metodo,
        OrigenPago origen,
        Long registradoPorId,
        String registradoPorNombre,
        String referencia,
        int notasAbonadas,
        BigDecimal montoAplicado,
        BigDecimal saldoAFavor) {
}
