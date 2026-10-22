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
        /** Pagos de mas o anticipos que todavia no se aplican a ninguna nota. */
        BigDecimal saldoAFavor,
        Double latitud,
        Double longitud,
        Integer ordenVisita,
        Boolean activo) {
}
