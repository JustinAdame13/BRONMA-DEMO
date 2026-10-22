package mx.bronma.cobranza.riesgo;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MotorAyaxTest {

    private static SenalesCliente senales(String saldo, String vencido, int diasMax, Double retraso,
                                          Double tendencia, Double uso, Double frecuencia, Integer sinComprar) {
        return new SenalesCliente(1L, "Tienda", new BigDecimal(saldo), new BigDecimal(vencido), diasMax,
                retraso, tendencia, uso, frecuencia, sinComprar);
    }

    @Test
    void buenPagadorEsBajoSinAccion() {
        ResultadoAyax r = MotorAyax.evaluar(senales("300", "0", 0, 0.5, 0.0, 0.2, 7.0, 3));
        assertEquals(NivelRiesgo.BAJO, r.nivel());
        assertEquals(AccionRecomendada.SIN_ACCION, r.accion());
        assertTrue(r.score() < 10);
    }

    @Test
    void conVencidoSeCobraEnLaProximaVisita() {
        ResultadoAyax r = MotorAyax.evaluar(senales("1500", "600", 12, 9.0, 0.3, 0.8, 7.0, 5));
        assertEquals(AccionRecomendada.COBRAR_PROXIMA_VISITA, r.accion());
    }

    @Test
    void morosoGraveSeSuspende() {
        ResultadoAyax r = MotorAyax.evaluar(senales("8000", "7000", 75, 28.0, 0.6, 1.3, 10.0, 40));
        assertEquals(NivelRiesgo.ALTO, r.nivel());
        assertEquals(AccionRecomendada.SUSPENDER_CREDITO, r.accion());
        assertTrue(r.explicacion().contains("dejó de comprar"));
    }

    @Test
    void clienteNuevoSinHistorialNoEsRiesgoso() {
        ResultadoAyax r = MotorAyax.evaluar(senales("500", "0", 0, null, null, null, null, 2));
        assertEquals(0, r.score());
        assertEquals(NivelRiesgo.BAJO, r.nivel());
    }

    @Test
    void elScoreNuncaPasaDeCien() {
        ResultadoAyax r = MotorAyax.evaluar(senales("99999", "99999", 400, 500.0, 50.0, 30.0, 1.0, 300));
        assertEquals(100, r.score());
    }
}
