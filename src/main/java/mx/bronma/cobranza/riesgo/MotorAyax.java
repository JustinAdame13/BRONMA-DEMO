package mx.bronma.cobranza.riesgo;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Ayax sin IA (modelo BASE): reglas fijas, explicables y deterministas.
 * No depende de Spring ni de la base, por eso se prueba con JUnit directo.
 *
 * Score de 0 (paga seguro) a 100 (no va a pagar), suma de cinco senales:
 * <pre>
 *  Retraso historico   hasta 35  dias promedio de retraso; 30 o mas = maximo
 *  Atraso actual       hasta 25  dias de la nota mas atrasada; 60 o mas = maximo
 *  Uso del credito     hasta 20  0 si usa la mitad o menos; maximo al 120 % del limite
 *  Deuda creciendo     hasta 10  la deuda crecio 100 % o mas en 30 dias = maximo
 *  Dejo de comprar     10        debe y lleva mas del doble de su frecuencia sin comprar
 * </pre>
 * Nivel: BAJO &lt; 35 &lt;= MEDIO &lt; 65 &lt;= ALTO.
 */
public final class MotorAyax {

    static final int LIMITE_MEDIO = 35;
    static final int LIMITE_ALTO = 65;

    private MotorAyax() {
    }

    public static ResultadoAyax evaluar(SenalesCliente s) {

        List<String> motivos = new ArrayList<>();
        double total = 0;

        // 1. Retraso historico
        if (s.diasPromedioRetraso() != null && s.diasPromedioRetraso() > 0) {
            double pts = Math.min(s.diasPromedioRetraso() / 30.0, 1.0) * 35;
            total += pts;
            if (pts >= 5) {
                motivos.add(String.format(Locale.ROOT, "paga en promedio %.0f días tarde", s.diasPromedioRetraso()));
            }
        }

        // 2. Atraso actual
        boolean tieneVencido = s.saldoVencido() != null && s.saldoVencido().signum() > 0;
        if (tieneVencido && s.diasAtrasoMax() > 0) {
            double pts = Math.min(s.diasAtrasoMax() / 60.0, 1.0) * 25;
            total += pts;
            motivos.add(String.format(Locale.ROOT, "tiene %s vencido y su nota más atrasada lleva %d días",
                    pesos(s.saldoVencido()), s.diasAtrasoMax()));
        }

        // 3. Uso del credito
        if (s.usoCredito() != null && s.usoCredito() > 0.5) {
            double pts = Math.min((s.usoCredito() - 0.5) / 0.7, 1.0) * 20;
            total += pts;
            motivos.add(String.format(Locale.ROOT, "usa el %.0f%% de su límite de crédito", s.usoCredito() * 100));
        }

        // 4. Deuda creciendo
        if (s.tendenciaDeuda() != null && s.tendenciaDeuda() > 0) {
            double pts = Math.min(s.tendenciaDeuda(), 1.0) * 10;
            total += pts;
            if (pts >= 2) {
                motivos.add(String.format(Locale.ROOT, "su deuda creció %.0f%% en 30 días", s.tendenciaDeuda() * 100));
            }
        }

        // 5. Dejo de comprar pero debe: senal clasica de cliente que se va
        boolean debe = s.saldo() != null && s.saldo().signum() > 0;
        if (debe && s.frecuenciaCompraDias() != null && s.frecuenciaCompraDias() > 0
                && s.diasSinComprar() != null && s.diasSinComprar() > 2 * s.frecuenciaCompraDias()) {
            total += 10;
            motivos.add(String.format(Locale.ROOT, "dejó de comprar: compraba cada %.0f días y lleva %d sin comprar",
                    s.frecuenciaCompraDias(), s.diasSinComprar()));
        }

        int score = (int) Math.max(0, Math.min(100, Math.round(total)));
        NivelRiesgo nivel = score >= LIMITE_ALTO ? NivelRiesgo.ALTO
                : score >= LIMITE_MEDIO ? NivelRiesgo.MEDIO
                : NivelRiesgo.BAJO;

        AccionRecomendada accion = accion(nivel, s, tieneVencido);
        String explicacion = motivos.isEmpty()
                ? "Paga a tiempo y no tiene saldo vencido."
                : capitalizar(String.join("; ", motivos)) + ".";

        return new ResultadoAyax(score, nivel, accion, explicacion);
    }

    static AccionRecomendada accion(NivelRiesgo nivel, SenalesCliente s, boolean tieneVencido) {
        boolean pasadoDeLimite = s.usoCredito() != null && s.usoCredito() > 1.0;
        return switch (nivel) {
            case ALTO -> (s.diasAtrasoMax() > 30 || pasadoDeLimite)
                    ? AccionRecomendada.SUSPENDER_CREDITO
                    : AccionRecomendada.REDUCIR_CREDITO;
            // Con vencido hay que ir por el dinero; sin vencido, un incentivo para que no se atrase
            case MEDIO -> tieneVencido
                    ? AccionRecomendada.COBRAR_PROXIMA_VISITA
                    : AccionRecomendada.OFRECER_DESCUENTO_PRONTO_PAGO;
            case BAJO -> tieneVencido
                    ? AccionRecomendada.COBRAR_PROXIMA_VISITA
                    : AccionRecomendada.SIN_ACCION;
        };
    }

    /** $11,010.00: el formato que se usa en Mexico para pesos. */
    static String pesos(BigDecimal monto) {
        DecimalFormat f = new DecimalFormat("$#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
        f.setRoundingMode(java.math.RoundingMode.HALF_UP);
        return f.format(monto);
    }

    private static String capitalizar(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** Para guardar en NUMERIC con la escala de la columna. */
    static BigDecimal decimal(Double valor, int escala) {
        return valor == null ? null : BigDecimal.valueOf(valor).setScale(escala, java.math.RoundingMode.HALF_UP);
    }
}
