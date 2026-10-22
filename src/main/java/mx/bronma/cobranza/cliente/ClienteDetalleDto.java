package mx.bronma.cobranza.cliente;

import mx.bronma.cobranza.nota.NotaDto;
import mx.bronma.cobranza.riesgo.EvaluacionDto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Respuesta de GET /clientes/{id}.
 *
 * notasAbiertas: las notas con saldo, la que vence antes primero.
 * riesgo: la evaluacion vigente de Ayax; null si el cliente no se ha evaluado.
 */
public record ClienteDetalleDto(
        Long id,
        String codigoOrigen,
        String nombreNegocio,
        Long rutaId,
        String rutaNombre,
        String contacto,
        String telefonoWhatsapp,
        String direccion,
        EstadoCredito estadoCredito,
        BigDecimal saldo,
        BigDecimal saldoVencido,
        /** Pagos de mas o anticipos que todavia no se aplican; se usan solos en la siguiente nota. */
        BigDecimal saldoAFavor,

        /** Lo capturado: 0 significa "usar el default de configuracion". */
        BigDecimal limiteCredito,
        /** El que de verdad aplica, ya resuelto el default. */
        BigDecimal limiteCreditoEfectivo,

        /** Lo capturado: null significa "usar el default de configuracion". */
        Integer plazoDias,
        /** El que de verdad aplica. */
        Integer plazoDiasEfectivo,

        /** saldo / limiteCreditoEfectivo. null si no hay limite con que comparar. */
        BigDecimal usoCredito,

        Boolean aceptaWhatsapp,
        Double latitud,
        Double longitud,
        UbicacionFuente ubicacionFuente,
        Instant ubicacionFijadaEn,
        Integer ordenVisita,
        Boolean activo,
        List<NotaDto> notasAbiertas,
        EvaluacionDto riesgo) {
}
