package mx.bronma.cobranza.nota;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Cuerpo de POST /notas: captura manual de una venta a credito (lo normal es
 * que lleguen por la importacion).
 *
 * @param fechaVencimiento opcional: sin ella se usa fechaEmision + plazo del cliente
 *                         (o el de configuracion).
 */
public record NotaRequest(
        @NotBlank @Size(max = 40) String folioOrigen,
        @NotNull Long clienteId,
        @NotNull LocalDate fechaEmision,
        LocalDate fechaVencimiento,
        @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal monto) {
}
