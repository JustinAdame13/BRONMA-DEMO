package mx.bronma.cobranza.nota;

import mx.bronma.cobranza.pago.MetodoPago;
import mx.bronma.cobranza.pago.OrigenPago;

import java.math.BigDecimal;
import java.time.Instant;

/** Un abono que recibio la nota: que parte de que pago se le aplico. */
public record AbonoNotaDto(
        Long pagoId,
        Instant fechaPago,
        MetodoPago metodo,
        OrigenPago origen,
        String referencia,
        BigDecimal monto) {
}
