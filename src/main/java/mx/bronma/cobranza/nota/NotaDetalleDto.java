package mx.bronma.cobranza.nota;

import java.util.List;

/** Respuesta de GET /notas/{id}: la nota y su historial de abonos (el mas reciente primero). */
public record NotaDetalleDto(NotaDto nota, List<AbonoNotaDto> abonos) {
}
