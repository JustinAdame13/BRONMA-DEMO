package mx.bronma.cobranza.riesgo;

import java.math.BigDecimal;

/**
 * Lo que Ayax sabe de un cliente al momento de evaluarlo. Se arma con una sola
 * consulta para todos los clientes (ver {@link RiesgoConsultas}).
 *
 * @param diasPromedioRetraso  atraso promedio ponderado por monto: lo pagado (dias tarde al pagar)
 *                             mas lo que hoy esta vencido (dias que lleva). null si no hay historial.
 * @param tendenciaDeuda       (saldo hoy - saldo hace 30 dias) / saldo hace 30 dias. null si no aplica.
 * @param usoCredito           saldo / limite efectivo. null si no hay limite.
 * @param frecuenciaCompraDias dias promedio entre notas (ultimos 180 dias). null con menos de 2 notas.
 * @param diasSinComprar       dias desde la ultima nota.
 */
public record SenalesCliente(
        Long clienteId,
        String nombreNegocio,
        BigDecimal saldo,
        BigDecimal saldoVencido,
        int diasAtrasoMax,
        Double diasPromedioRetraso,
        Double tendenciaDeuda,
        Double usoCredito,
        Double frecuenciaCompraDias,
        Integer diasSinComprar) {
}
