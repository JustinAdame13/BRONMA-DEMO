package mx.bronma.cobranza.importacion;

/**
 * Un problema encontrado al importar. Es el formato de importaciones.detalle_errores:
 * [{"fila": 12, "folio": "A-123", "error": "..."}].
 *
 * @param fila  fila del archivo como la ve la oficina (la primera de la nota si
 *              el folio viene en varias filas).
 * @param folio null si la fila ni siquiera traia folio.
 */
public record ErrorFila(Integer fila, String folio, String error) {
}
