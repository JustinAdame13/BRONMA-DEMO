package mx.bronma.cobranza.pago;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Cuerpo de POST /pagos.
 *
 * @param fechaPago       cuando se cobro; null = ahora. El celular manda la hora real
 *                        del cobro aunque lo suba despues por falta de senal.
 * @param registradoPorId obligatorio si origen = RUTA (quien cobro).
 * @param uuidDispositivo lo genera el celular una vez por cobro; si reintenta con el
 *                        mismo, se regresa el pago que ya existe en vez de duplicarlo.
 * @param aplicaciones    opcional. Vacio = se reparte solo, primero lo que vence antes.
 *                        Si viene, la suma tiene que ser igual al monto.
 */
public record PagoRequest(
        @NotNull Long clienteId,
        @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal monto,
        Instant fechaPago,
        @NotNull MetodoPago metodo,
        @NotNull OrigenPago origen,
        Long registradoPorId,
        @Size(max = 100) String referencia,
        @Size(max = 255) String comentario,
        UUID uuidDispositivo,
        @Valid List<AplicacionRequest> aplicaciones) {

    /** Cuanto del pago va a una nota en particular. */
    public record AplicacionRequest(
            @NotNull Long notaId,
            @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal monto) {
    }
}
