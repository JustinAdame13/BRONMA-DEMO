package mx.bronma.cobranza.importacion;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * Las columnas que entiende la importacion y los nombres con que pueden venir
 * en el archivo del sistema de ventas.
 *
 * Todavia no sabemos que columnas exporta el sistema de la distribuidora, asi
 * que cada columna acepta varios nombres. Cuando llegue el archivo real solo
 * hay que agregar su encabezado a la lista: la logica no cambia.
 *
 * Los alias se comparan ya normalizados (ver {@link #normalizar}): sin
 * acentos, en minusculas y sin espacios ni signos. "No. Nota", "NO_NOTA" y
 * "no nota" quedan todos como "nonota".
 *
 * Los alias secundarios son nombres ambiguos ("cliente" puede ser la clave o
 * el nombre). Solo se usan si la columna no se encontro con un alias primario.
 */
public enum ColumnaImportacion {

    FOLIO(true,
            List.of("folio", "nota", "nonota", "nodenota", "numnota", "numeronota", "numerodenota", "folionota", "foliodenota",
                    "notaventa", "notadeventa", "remision", "noremision", "factura", "documento",
                    "nodocumento", "ticket"),
            List.of()),

    FECHA_EMISION(true,
            List.of("fecha", "fechaemision", "fechadeemision", "fechaventa", "fechadeventa", "fechanota",
                    "emision"),
            List.of()),

    CLIENTE_CODIGO(true,
            List.of("codigocliente", "codigodecliente", "clavecliente", "clavedecliente", "cvecliente",
                    "codcliente", "idcliente", "numcliente", "nocliente", "nodecliente", "numerocliente",
                    "numerodecliente", "clientecodigo", "clienteid", "clienteclave"),
            List.of("cliente", "clave", "codigo")),

    MONTO(true,
            List.of("monto", "total", "importe", "importetotal", "totalnota", "montototal", "totalventa"),
            List.of()),

    // ---------- Opcionales ----------

    /** Sin esta columna, el vencimiento se calcula con el plazo. */
    FECHA_VENCIMIENTO(false,
            List.of("vencimiento", "fechavencimiento", "fechadevencimiento", "vence", "fechavence",
                    "fechalimite", "fechalimitedepago"),
            List.of()),

    /** Obligatoria solo si el cliente es nuevo: sin nombre no se le puede dar de alta. */
    CLIENTE_NOMBRE(false,
            List.of("nombrecliente", "nombredelcliente", "nombre", "negocio", "nombrenegocio",
                    "nombredelnegocio", "razonsocial", "tienda", "nombretienda", "establecimiento"),
            List.of("cliente")),

    RUTA(false,
            List.of("ruta", "codigoruta", "claveruta", "cveruta", "rutacodigo", "noruta", "numruta",
                    "rutareparto", "rutadereparto"),
            List.of()),

    /** CONTADO se omite. Sin esta columna, todas las filas se toman como credito. */
    TIPO_VENTA(false,
            List.of("tipo", "tipoventa", "tipodeventa", "condicion", "condicionpago", "condiciondepago",
                    "formapago", "formadepago", "metodopago", "metododepago"),
            List.of()),

    /** Dias de credito de esta nota. Solo se usa si no viene FECHA_VENCIMIENTO. */
    PLAZO_DIAS(false,
            List.of("plazo", "plazodias", "diascredito", "diasdecredito", "diasplazo", "diasdeplazo"),
            List.of()),

    TELEFONO(false,
            List.of("telefono", "tel", "celular", "cel", "whatsapp", "telefonowhatsapp", "movil"),
            List.of()),

    DIRECCION(false,
            List.of("direccion", "domicilio", "calle"),
            List.of());

    private final boolean obligatoria;
    private final List<String> aliasPrimarios;
    private final List<String> aliasSecundarios;

    ColumnaImportacion(boolean obligatoria, List<String> aliasPrimarios, List<String> aliasSecundarios) {
        this.obligatoria = obligatoria;
        this.aliasPrimarios = aliasPrimarios;
        this.aliasSecundarios = aliasSecundarios;
    }

    public boolean isObligatoria() {
        return obligatoria;
    }

    public List<String> getAliasPrimarios() {
        return aliasPrimarios;
    }

    public List<String> getAliasSecundarios() {
        return aliasSecundarios;
    }

    /** "Fecha de Emisión " -> "fechadeemision". */
    public static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        String sinAcentos = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return sinAcentos.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
