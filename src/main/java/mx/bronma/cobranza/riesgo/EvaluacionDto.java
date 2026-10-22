package mx.bronma.cobranza.riesgo;

import mx.bronma.cobranza.Enums.Enums;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Una evaluacion de Ayax. Trae las senales con que se calculo para que la
 * oficina pueda ver POR QUE un cliente salio riesgoso.
 *
 * @param modelo BASE = reglas de Ayax; IA = Ayax + modelo de lenguaje.
 */
public record EvaluacionDto(
        Long id,
        Long clienteId,
        String clienteNombre,
        Long rutaId,
        LocalDate fechaCalculo,
        Enums.ModeloAyax modelo,
        int score,
        NivelRiesgo nivel,
        BigDecimal diasPromedioRetraso,
        BigDecimal tendenciaDeuda,
        BigDecimal usoCredito,
        BigDecimal frecuenciaCompraDias,
        BigDecimal saldoVencido,
        AccionRecomendada accionRecomendada,
        String explicacion,
        Instant generadaEn) {
}
