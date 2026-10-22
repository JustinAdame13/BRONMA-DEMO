package mx.bronma.cobranza.importacion;

import java.time.Instant;

/**
 * Cada elemento de GET /importaciones (sin el detalle de errores).
 *
 * Unidades: filasTotal y omitidas cuentan FILAS del archivo; creadas,
 * actualizadas y sinCambios cuentan NOTAS (un folio puede venir en varias
 * filas); errores cuenta los renglones de detalleErrores.
 */
public record ImportacionResumenDto(
        Long id,
        String archivoNombre,
        Instant importadoEn,
        Long usuarioId,
        int filasTotal,
        int creadas,
        int actualizadas,
        int sinCambios,
        int omitidas,
        int errores,
        int clientesNuevos,
        int rutasNuevas) {

    public static ImportacionResumenDto from(Importacion i) {
        return new ImportacionResumenDto(
                i.getId(),
                i.getArchivoNombre(),
                i.getImportadoEn(),
                i.getUsuario() == null ? null : i.getUsuario().getId(),
                i.getFilasTotal(),
                i.getCreadas(),
                i.getActualizadas(),
                i.getSinCambios(),
                i.getOmitidas(),
                i.getErrores(),
                i.getClientesNuevos(),
                i.getRutasNuevas());
    }
}
