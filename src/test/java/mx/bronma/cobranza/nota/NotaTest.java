package mx.bronma.cobranza.nota;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** El estado de la nota siempre tiene que cuadrar con su saldo (igual que el CHECK de V1). */
class NotaTest {

    private static Nota nota(String monto) {
        Nota n = new Nota();
        n.setFolioOrigen("A-1");
        n.setFechaEmision(LocalDate.of(2026, 10, 1));
        n.setFechaVencimiento(LocalDate.of(2026, 10, 15));
        n.setMonto(new BigDecimal(monto));
        n.setSaldo(new BigDecimal(monto));
        n.setEstado(EstadoNota.ABIERTA);
        return n;
    }

    @Test
    void abonoParcialYLuegoLiquida() {
        Nota n = nota("500.00");

        n.abonar(new BigDecimal("200.00"));
        assertEquals(new BigDecimal("300.00"), n.getSaldo());
        assertEquals(EstadoNota.PARCIAL, n.getEstado());

        n.abonar(new BigDecimal("300.00"));
        assertEquals(0, n.getSaldo().signum());
        assertEquals(EstadoNota.PAGADA, n.getEstado());
        assertFalse(n.estaViva());
    }

    @Test
    void noSeAbonaMasDeLoQueSeDebe() {
        Nota n = nota("100.00");
        assertThrows(IllegalArgumentException.class, () -> n.abonar(new BigDecimal("100.01")));
    }

    @Test
    void canceladaNoRecibeAbonos() {
        Nota n = nota("100.00");
        n.setEstado(EstadoNota.CANCELADA);
        assertThrows(IllegalStateException.class, () -> n.abonar(new BigDecimal("10.00")));
    }

    @Test
    void revertirRegresaElEstado() {
        Nota n = nota("500.00");
        n.abonar(new BigDecimal("500.00"));

        n.revertirAbono(new BigDecimal("200.00"));
        assertEquals(EstadoNota.PARCIAL, n.getEstado());

        n.revertirAbono(new BigDecimal("300.00"));
        assertEquals(EstadoNota.ABIERTA, n.getEstado());
        assertTrue(n.estaViva());

        assertThrows(IllegalStateException.class, () -> n.revertirAbono(new BigDecimal("0.01")));
    }

    @Test
    void vencida() {
        Nota n = nota("100.00");
        assertFalse(n.estaVencida(LocalDate.of(2026, 10, 15))); // el dia que vence aun no esta vencida
        assertTrue(n.estaVencida(LocalDate.of(2026, 10, 16)));
        assertEquals(5, n.diasVencida(LocalDate.of(2026, 10, 20)));
    }
}
