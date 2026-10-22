package mx.bronma.cobranza.recordatorio;

import java.time.Instant;
import java.time.LocalDate;

/** Cada elemento de GET /recordatorios. mensaje y telefonoDestino son la foto de lo que se mando. */
public record RecordatorioDto(
        Long id,
        Long notaId,
        String folio,
        Long clienteId,
        String clienteNombre,
        Long politicaId,
        int diasOffset,
        LocalDate programadoPara,
        EstadoRecordatorio estado,
        int intentos,
        String telefonoDestino,
        String mensaje,
        String mensajeProveedorId,
        String error,
        Instant enviadoEn) {
}
