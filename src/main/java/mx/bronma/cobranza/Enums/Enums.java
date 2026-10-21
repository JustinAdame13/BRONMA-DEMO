package mx.bronma.cobranza.Enums;

public class Enums {

    /** Quien calculo una evaluacion de riesgo: Ayax solo (BASE) o Ayax + IA. */
    public enum ModeloAyax { BASE, IA }

    public enum ProveedorIA {
        ANTHROPIC,
        OPENAI,
        GEMINI,
        NINGUNO
    }
}
