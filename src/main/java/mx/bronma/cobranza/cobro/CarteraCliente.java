package mx.bronma.cobranza.cobro;

import mx.bronma.cobranza.cliente.EstadoCredito;
import mx.bronma.cobranza.riesgo.AccionRecomendada;
import mx.bronma.cobranza.riesgo.NivelRiesgo;

import java.math.BigDecimal;

/**
 * Foto de la cartera de un cliente: lo que sale de {@link CarteraConsultas}
 * y alimenta la lista de cobro, el mapa y el dashboard.
 *
 * @param exigible   saldo que ya vencio o vence hoy: lo que hay que cobrar en la visita.
 * @param porVencer  saldo que vence en los proximos dias (ventana de la consulta).
 */
public record CarteraCliente(
        Long clienteId,
        String codigoOrigen,
        String nombreNegocio,
        String contacto,
        String telefonoWhatsapp,
        String direccion,
        Long rutaId,
        String rutaNombre,
        String rutaColor,
        Double latitud,
        Double longitud,
        Integer ordenVisita,
        EstadoCredito estadoCredito,
        BigDecimal saldo,
        BigDecimal saldoVencido,
        BigDecimal exigible,
        BigDecimal porVencer,
        int diasAtrasoMax,
        int notasVencidas,
        Integer scoreRiesgo,
        NivelRiesgo nivelRiesgo,
        AccionRecomendada accionRecomendada) {

    public PrioridadCobro.Prioridad prioridad() {
        return PrioridadCobro.calcular(exigible, diasAtrasoMax, scoreRiesgo, nivelRiesgo, porVencer);
    }
}
