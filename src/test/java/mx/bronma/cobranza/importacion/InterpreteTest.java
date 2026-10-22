package mx.bronma.cobranza.importacion;

import mx.bronma.cobranza.importacion.Interprete.TipoVenta;
import mx.bronma.cobranza.importacion.Interprete.ValorInvalidoException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Pruebas sin Spring ni base de datos: corren en milisegundos. */
class InterpreteTest {

    @Test
    void montosEnLosFormatosComunes() {
        assertEquals(new BigDecimal("1234.50"), Interprete.monto("$1,234.50"));
        assertEquals(new BigDecimal("1234.50"), Interprete.monto("1234.5"));
        assertEquals(new BigDecimal("1234.50"), Interprete.monto("1.234,50"));
        assertEquals(new BigDecimal("1234.50"), Interprete.monto("1234,50"));
        assertEquals(new BigDecimal("1234.00"), Interprete.monto("1,234"));
        assertEquals(new BigDecimal("99.00"), Interprete.monto("MXN 99"));
    }

    @Test
    void montosInvalidos() {
        assertThrows(ValorInvalidoException.class, () -> Interprete.monto("0"));
        assertThrows(ValorInvalidoException.class, () -> Interprete.monto("-5"));
        assertThrows(ValorInvalidoException.class, () -> Interprete.monto("abc"));
        assertThrows(ValorInvalidoException.class, () -> Interprete.monto("12345678901"));
    }

    @Test
    void fechasDiaAntesQueMes() {
        LocalDate esperada = LocalDate.of(2026, 10, 21);
        assertEquals(esperada, Interprete.fecha("21/10/2026", "fecha"));
        assertEquals(esperada, Interprete.fecha("2026-10-21", "fecha"));
        assertEquals(esperada, Interprete.fecha("21-10-2026", "fecha"));
        assertEquals(esperada, Interprete.fecha("21/10/26", "fecha"));
        assertEquals(esperada, Interprete.fecha("21/10/2026 00:00:00", "fecha"));
        assertEquals(LocalDate.of(2026, 2, 1), Interprete.fecha("1/2/2026", "fecha"));
    }

    @Test
    void fechasImposiblesSonError() {
        assertThrows(ValorInvalidoException.class, () -> Interprete.fecha("31/02/2026", "fecha"));
        // Formato gringo: mes 21 no existe
        assertThrows(ValorInvalidoException.class, () -> Interprete.fecha("10/21/2026", "fecha"));
    }

    @Test
    void telefonosAE164() {
        assertEquals("+528711234567", Interprete.telefonoE164("871 123 4567"));
        assertEquals("+528711234567", Interprete.telefonoE164("(871)123-45-67"));
        assertEquals("+528711234567", Interprete.telefonoE164("+52 871 123 4567"));
        assertEquals("+528711234567", Interprete.telefonoE164("528711234567"));
        assertEquals("+528711234567", Interprete.telefonoE164("5218711234567"));
        assertNull(Interprete.telefonoE164("12345"));
        assertNull(Interprete.telefonoE164(null));
    }

    @Test
    void tipoDeVenta() {
        assertEquals(TipoVenta.CREDITO, Interprete.tipoVenta(null));
        assertEquals(TipoVenta.CREDITO, Interprete.tipoVenta("Crédito"));
        assertEquals(TipoVenta.CONTADO, Interprete.tipoVenta("CONTADO"));
        assertEquals(TipoVenta.CONTADO, Interprete.tipoVenta("Efectivo"));
        assertThrows(ValorInvalidoException.class, () -> Interprete.tipoVenta("otro"));
    }

    @Test
    void plazo() {
        assertEquals(15, Interprete.plazoDias("15"));
        assertEquals(15, Interprete.plazoDias("15.0"));
        assertEquals(15, Interprete.plazoDias("15 dias"));
        assertThrows(ValorInvalidoException.class, () -> Interprete.plazoDias("x"));
    }
}
