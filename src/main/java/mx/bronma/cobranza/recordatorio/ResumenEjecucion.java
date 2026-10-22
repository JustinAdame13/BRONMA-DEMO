package mx.bronma.cobranza.recordatorio;

import java.time.LocalDate;

/**
 * Resultado de una corrida de recordatorios (POST /recordatorios/ejecutar o la diaria).
 *
 * @param generados  recordatorios nuevos programados (nota viva x regla activa).
 * @param cancelados ya no aplicaban: nota pagada/cancelada, cliente sin consentimiento o sin telefono,
 *                   o se quedaron sin mandar mas de 2 dias.
 * @param aviso      por ejemplo "WhatsApp deshabilitado": se programaron pero no se enviaron.
 */
public record ResumenEjecucion(
        LocalDate fecha,
        String enviador,
        int generados,
        int enviados,
        int fallidos,
        int cancelados,
        String aviso) {
}
