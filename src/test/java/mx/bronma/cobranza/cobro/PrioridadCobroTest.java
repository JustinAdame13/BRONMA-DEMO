package mx.bronma.cobranza.cobro;

import mx.bronma.cobranza.riesgo.NivelRiesgo;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrioridadCobroTest {

    @Test
    void morosoRiesgosoVaPrimeroQueUnoQueApenasSeAtraso() {
        int moroso = PrioridadCobro.calcular(new BigDecimal("7000"), 75, 94, NivelRiesgo.ALTO, BigDecimal.ZERO).puntos();
        int leve = PrioridadCobro.calcular(new BigDecimal("600"), 12, 27, NivelRiesgo.BAJO, new BigDecimal("200")).puntos();
        assertTrue(moroso > leve);
        assertTrue(moroso <= 100);
    }

    @Test
    void alCorrienteSinNadaPorVencerEsCero() {
        PrioridadCobro.Prioridad p = PrioridadCobro.calcular(BigDecimal.ZERO, 0, null, null, BigDecimal.ZERO);
        assertEquals(0, p.puntos());
        assertEquals("Al corriente", p.motivo());
    }

    @Test
    void loQueVenceProntoSumaDiez() {
        assertEquals(10, PrioridadCobro.calcular(BigDecimal.ZERO, 0, null, null, new BigDecimal("300")).puntos());
    }
}
