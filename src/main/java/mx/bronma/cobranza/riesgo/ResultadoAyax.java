package mx.bronma.cobranza.riesgo;

/** Lo que concluye Ayax de un cliente. */
public record ResultadoAyax(int score, NivelRiesgo nivel, AccionRecomendada accion, String explicacion) {
}
