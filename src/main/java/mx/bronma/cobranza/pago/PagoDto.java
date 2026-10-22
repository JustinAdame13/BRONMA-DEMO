package mx.bronma.cobranza.pago;

import mx.bronma.cobranza.nota.EstadoNota;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Respuesta de POST /pagos y GET /pagos/{id}. Sirve de comprobante: dice a que
 * notas se abono y como quedo cada una.
 *
 * @param montoAplicado      cuanto de este pago se abono a notas.
 * @param saldoAFavor        lo que de este pago sigue sin aplicar (monto - montoAplicado).
 * @param saldoCliente       lo que el cliente sigue debiendo (al momento de la respuesta).
 * @param saldoAFavorCliente todo lo que el cliente tiene a favor (de este y otros pagos).
 */
public record PagoDto(
        Long id,
        Long clienteId,
        String clienteNombre,
        BigDecimal monto,
        Instant fechaPago,
        MetodoPago metodo,
        OrigenPago origen,
        Long registradoPorId,
        String registradoPorNombre,
        String referencia,
        String comentario,
        UUID uuidDispositivo,
        Instant creadoEn,
        List<Aplicacion> aplicaciones,
        BigDecimal montoAplicado,
        BigDecimal saldoAFavor,
        BigDecimal saldoCliente,
        BigDecimal saldoAFavorCliente) {

    /**
     * @param saldoNota  saldo de la nota al momento de la respuesta.
     * @param estadoNota PAGADA si este pago (o uno posterior) la liquido.
     */
    public record Aplicacion(Long notaId, String folio, BigDecimal monto, BigDecimal saldoNota, EstadoNota estadoNota) {
    }
}
