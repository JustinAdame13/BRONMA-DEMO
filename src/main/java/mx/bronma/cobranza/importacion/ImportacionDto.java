package mx.bronma.cobranza.importacion;

import java.time.Instant;
import java.util.List;

/**
 * Respuesta de POST /importaciones y de GET /importaciones/{id}.
 *
 * @param simulada      true si se subio con ?simular=true: se reviso todo pero no
 *                      se guardo nada (id e importadoEn llegan en null).
 * @param detalleErrores maximo {@code ImportacionService.MAXIMO_ERRORES_DETALLE};
 *                      "errores" siempre trae el total real.
 */
public record ImportacionDto(
        Long id,
        String archivoNombre,
        Instant importadoEn,
        Long usuarioId,
        boolean simulada,
        int filasTotal,
        int creadas,
        int actualizadas,
        int sinCambios,
        int omitidas,
        int errores,
        int clientesNuevos,
        int rutasNuevas,
        List<ErrorFila> detalleErrores) {
}
