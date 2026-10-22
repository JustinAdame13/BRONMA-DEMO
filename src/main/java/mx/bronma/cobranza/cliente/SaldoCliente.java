package mx.bronma.cobranza.cliente;

import java.math.BigDecimal;

/**
 * Saldos de un cliente: lo que debe en notas vivas (no canceladas y con saldo)
 * y lo que tiene a favor (pagos sin aplicar).
 */
public record SaldoCliente(BigDecimal saldo, BigDecimal saldoVencido, BigDecimal saldoAFavor) {

    public static final SaldoCliente CERO = new SaldoCliente(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
}
