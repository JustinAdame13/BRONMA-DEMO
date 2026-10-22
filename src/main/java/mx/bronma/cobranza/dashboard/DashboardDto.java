package mx.bronma.cobranza.dashboard;

import mx.bronma.cobranza.riesgo.NivelRiesgo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Respuesta de GET /dashboard. La cartera es una foto de HOY; la cobranza es
 * del periodo [desde, hasta].
 *
 * @param porcentajeVencido carteraVencida / carteraTotal * 100 (un decimal).
 */
public record DashboardDto(
        LocalDate fecha,
        LocalDate desde,
        LocalDate hasta,
        Cartera cartera,
        List<Antiguedad> antiguedad,
        List<Deudor> topDeudores,
        Cobranza cobranza,
        List<PorRuta> porRuta,
        Map<NivelRiesgo, Integer> clientesPorRiesgo) {

    public record Cartera(
            BigDecimal total,
            BigDecimal vencida,
            BigDecimal alCorriente,
            BigDecimal porcentajeVencido,
            int clientesConSaldo,
            int clientesConVencido,
            /** Pagos de mas o anticipos sin aplicar: dinero que se le debe a los clientes. */
            BigDecimal saldoAFavor) {
    }

    /**
     * @param rango AL_CORRIENTE (no ha vencido), VENCIDO_1_30, VENCIDO_31_60 o VENCIDO_MAS_60 (dias de atraso).
     */
    public record Antiguedad(String rango, BigDecimal monto, int notas, int clientes) {
    }

    public record Deudor(
            Long clienteId,
            String nombreNegocio,
            Long rutaId,
            String rutaNombre,
            BigDecimal saldo,
            BigDecimal saldoVencido,
            int diasAtrasoMax,
            NivelRiesgo nivelRiesgo,
            int prioridad) {
    }

    /**
     * @param recuperadoPorRecordatorios abonos a notas que habian recibido un recordatorio
     *                                   de WhatsApp en los 7 dias anteriores al pago.
     * @param diasPromedioCobro          dias promedio entre la venta y el pago, ponderado por monto.
     *                                   null si no hubo pagos.
     */
    public record Cobranza(
            BigDecimal cobrado,
            int pagos,
            BigDecimal recuperadoPorRecordatorios,
            int recordatoriosEnviados,
            BigDecimal diasPromedioCobro) {
    }

    public record PorRuta(
            Long rutaId,
            String rutaNombre,
            String color,
            BigDecimal cartera,
            BigDecimal vencido,
            int clientesConSaldo,
            BigDecimal cobradoPeriodo) {
    }
}
