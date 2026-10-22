package mx.bronma.cobranza.cliente;

import java.math.BigDecimal;

/** Saldos de las notas vivas de un cliente (vivas = no canceladas y con saldo). */
public record SaldoCliente(BigDecimal saldo, BigDecimal saldoVencido) {

    public static final SaldoCliente CERO = new SaldoCliente(BigDecimal.ZERO, BigDecimal.ZERO);
}
