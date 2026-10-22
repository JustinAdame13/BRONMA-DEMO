package mx.bronma.cobranza.cliente;

import java.math.BigDecimal;

/** Cada elemento de la pagina que devuelve GET /clientes. */
public record ClienteResumenDto(
        Long id,
        String codigoOrigen,
        String nombreNegocio,
        Long rutaId,
        String rutaNombre,
        String telefonoWhatsapp,
        EstadoCredito estadoCredito,
        BigDecimal saldo,
        BigDecimal saldoVencido,
        Double latitud,
        Double longitud,
        Integer ordenVisita) {
}
