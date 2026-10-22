package mx.bronma.cobranza.importacion;

import java.util.Map;

/**
 * Una fila de datos del archivo, ya separada por columna.
 *
 * @param numero  numero de fila como lo ve la oficina al abrir el archivo
 *                (1 = primera fila, contando el encabezado), para que pueda
 *                encontrar la fila con error.
 * @param valores solo las columnas que trae el archivo y que tienen algo escrito.
 */
public record FilaArchivo(int numero, Map<ColumnaImportacion, String> valores) {

    /** null si la columna no viene en el archivo o esta vacia en esta fila. */
    public String valor(ColumnaImportacion columna) {
        return valores.get(columna);
    }

    public boolean tiene(ColumnaImportacion columna) {
        return valores.containsKey(columna);
    }
}
