package mx.bronma.cobranza.cobro;

import mx.bronma.cobranza.riesgo.NivelRiesgo;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A quien cobrarle primero. Una sola formula para la lista de cobro, el mapa
 * y el dashboard, asi el repartidor y la oficina ven el mismo orden.
 *
 * Puntos de 0 a 100:
 * <pre>
 *  Dias de atraso        hasta 35   60 dias o mas = maximo
 *  Monto exigible        hasta 30   escala logaritmica: $10,000 o mas = maximo
 *  Riesgo de Ayax        hasta 25   score de la evaluacion vigente
 *  Vence en pocos dias   10         tiene algo que vence pronto (cobrar antes de que se atrase)
 * </pre>
 * El monto va en escala logaritmica a proposito: $5,000 no es 10 veces mas
 * urgente que $500, y sin el logaritmo un solo cliente grande taparia a todos.
 */
public final class PrioridadCobro {

    private PrioridadCobro() {
    }

    public record Prioridad(int puntos, String motivo) {
    }

    public static Prioridad calcular(BigDecimal exigible, int diasAtrasoMax, Integer scoreRiesgo,
                                     NivelRiesgo nivelRiesgo, BigDecimal porVencer) {

        List<String> motivos = new ArrayList<>();
        double total = 0;

        if (diasAtrasoMax > 0) {
            total += Math.min(diasAtrasoMax, 60) / 60.0 * 35;
            motivos.add(diasAtrasoMax + (diasAtrasoMax == 1 ? " dia" : " dias") + " de atraso");
        }

        if (exigible != null && exigible.signum() > 0) {
            total += Math.min(Math.log10(1 + exigible.doubleValue()) / 4.0, 1.0) * 30;
            motivos.add(String.format(Locale.ROOT, "$%,.2f por cobrar", exigible.doubleValue()));
        }

        if (scoreRiesgo != null && scoreRiesgo > 0) {
            total += Math.min(scoreRiesgo, 100) / 100.0 * 25;
            if (nivelRiesgo != null && nivelRiesgo != NivelRiesgo.BAJO) {
                motivos.add("riesgo " + nivelRiesgo.name().toLowerCase(Locale.ROOT));
            }
        }

        if (porVencer != null && porVencer.signum() > 0) {
            total += 10;
            motivos.add(String.format(Locale.ROOT, "$%,.2f vence pronto", porVencer.doubleValue()));
        }

        int puntos = (int) Math.max(0, Math.min(100, Math.round(total)));
        String motivo = motivos.isEmpty() ? "Al corriente" : String.join(", ", motivos);
        return new Prioridad(puntos, Character.toUpperCase(motivo.charAt(0)) + motivo.substring(1));
    }
}
