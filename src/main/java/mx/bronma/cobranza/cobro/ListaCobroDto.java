package mx.bronma.cobranza.cobro;

import mx.bronma.cobranza.cliente.EstadoCredito;
import mx.bronma.cobranza.riesgo.AccionRecomendada;
import mx.bronma.cobranza.riesgo.NivelRiesgo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Respuesta de GET /lista-cobro: a quien visitar hoy y cuanto cobrarle.
 *
 * @param totalACobrar suma de montoACobrar (lo vencido o que vence hoy).
 */
public record ListaCobroDto(
        LocalDate fecha,
        Long rutaId,
        Long repartidorId,
        OrdenLista orden,
        int clientes,
        BigDecimal totalACobrar,
        BigDecimal totalPorVencer,
        List<Item> items) {

    /**
     * @param posicion     1 = el primero a visitar, segun el orden pedido.
     * @param montoACobrar lo que ya vencio o vence hoy.
     * @param porVencer    lo que vence en los proximos dias (se puede adelantar).
     * @param prioridad    0 a 100 (ver PrioridadCobro).
     * @param motivo       por que tiene esa prioridad, en una linea para el repartidor.
     */
    public record Item(
            int posicion,
            Long clienteId,
            String codigoOrigen,
            String nombreNegocio,
            String contacto,
            String telefonoWhatsapp,
            String direccion,
            Long rutaId,
            String rutaNombre,
            Double latitud,
            Double longitud,
            Integer ordenVisita,
            EstadoCredito estadoCredito,
            BigDecimal saldo,
            BigDecimal saldoVencido,
            BigDecimal montoACobrar,
            BigDecimal porVencer,
            int diasAtrasoMax,
            int notasVencidas,
            NivelRiesgo nivelRiesgo,
            AccionRecomendada accionRecomendada,
            int prioridad,
            String motivo) {
    }
}
