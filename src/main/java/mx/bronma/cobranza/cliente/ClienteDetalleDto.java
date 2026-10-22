package mx.bronma.cobranza.cliente;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Respuesta de GET /clientes/{id}.
 *
 * PENDIENTE: el contrato pide ademas "notasAbiertas" (NotaDto[]) y "riesgo"
 * (EvaluacionDto). Esos dos DTOs son del modulo de Justin (notas y Ayax) y se
 * agregan aqui cuando existan; inventarlos ahora los haria chocar con los suyos.
 */
public record ClienteDetalleDto(
        Long id,
        String codigoOrigen,
        String nombreNegocio,
        Long rutaId,
        String rutaNombre,
        String contacto,
        String telefonoWhatsapp,
        String direccion,
        EstadoCredito estadoCredito,
        BigDecimal saldo,
        BigDecimal saldoVencido,

        /** Lo capturado: 0 significa "usar el default de configuracion". */
        BigDecimal limiteCredito,
        /** El que de verdad aplica, ya resuelto el default. */
        BigDecimal limiteCreditoEfectivo,

        /** Lo capturado: null significa "usar el default de configuracion". */
        Integer plazoDias,
        /** El que de verdad aplica. */
        Integer plazoDiasEfectivo,

        /** saldo / limiteCreditoEfectivo. null si no hay limite con que comparar. */
        BigDecimal usoCredito,

        Boolean aceptaWhatsapp,
        Double latitud,
        Double longitud,
        UbicacionFuente ubicacionFuente,
        Instant ubicacionFijadaEn,
        Integer ordenVisita) {
}
