package mx.bronma.cobranza.pago;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SaldoAFavorTest {

    @Test
    void elRemanenteEsLoQueNoSeHaAplicado() {
        Pago p = new Pago();
        p.setMonto(new BigDecimal("1000.00"));
        assertEquals(new BigDecimal("1000.00"), SaldoAFavorService.remanente(p)); // anticipo completo

        AplicacionPago a = new AplicacionPago();
        a.setPago(p);
        a.setMonto(new BigDecimal("650.00"));
        p.getAplicaciones().add(a);
        assertEquals(new BigDecimal("350.00"), SaldoAFavorService.remanente(p));
    }
}
