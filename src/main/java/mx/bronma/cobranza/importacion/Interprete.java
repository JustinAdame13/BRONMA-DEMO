package mx.bronma.cobranza.importacion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Convierte los textos del archivo en valores del negocio.
 * Si un valor no se puede interpretar lanza {@link ValorInvalidoException}
 * con un mensaje que la oficina entienda; el servicio lo anota como error de
 * esa fila y sigue con las demas.
 */
final class Interprete {

    private Interprete() {
    }

    enum TipoVenta { CREDITO, CONTADO }

    /** Tope de NUMERIC(12,2). */
    private static final BigDecimal MONTO_MAXIMO = new BigDecimal("9999999999.99");

    private static final Pattern E164 = Pattern.compile("^\\+[1-9][0-9]{7,14}$");

    /**
     * Formatos de fecha que se aceptan. Dia antes que mes, como se escribe en
     * Mexico: 03/10/2026 es 3 de octubre, nunca 10 de marzo.
     * STRICT + "uuuu" para que 31/02/2026 sea error y no 3 de marzo.
     */
    private static final List<DateTimeFormatter> FORMATOS_FECHA = List.of(
            DateTimeFormatter.ofPattern("uuuu-M-d").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("d/M/uuuu").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("d-M-uuuu").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("d.M.uuuu").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("uuuu/M/d").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("d/M/uu").withResolverStyle(ResolverStyle.STRICT));

    static LocalDate fecha(String texto, String nombreCampo) {
        // "21/10/2026 00:00:00" o "2026-10-21T00:00": solo interesa la fecha
        String solo = texto.trim().split("[ T]", 2)[0];
        for (DateTimeFormatter f : FORMATOS_FECHA) {
            try {
                return LocalDate.parse(solo, f);
            } catch (DateTimeParseException ignorada) {
                // se prueba el siguiente formato
            }
        }
        throw new ValorInvalidoException(nombreCampo + " '" + texto + "' no es una fecha valida (usa dd/mm/aaaa)");
    }

    /**
     * "$1,234.50", "1234.5", "1.234,50" y "1234,50" son todos 1234.50.
     * Negativos son error: devoluciones y notas de credito no se manejan aqui.
     */
    static BigDecimal monto(String texto) {
        String limpio = texto.replaceAll("(?i)mxn|mn|\\$|\\s", "");

        int coma = limpio.lastIndexOf(',');
        int punto = limpio.lastIndexOf('.');
        if (coma >= 0 && punto >= 0) {
            // El que va al final es el separador decimal
            limpio = coma > punto
                    ? limpio.replace(".", "").replace(',', '.')
                    : limpio.replace(",", "");
        } else if (coma >= 0) {
            // Solo comas: "1234,50" es decimal; "1,234" o "1,234,567" son miles
            limpio = limpio.matches("-?\\d+,\\d{1,2}") ? limpio.replace(',', '.') : limpio.replace(",", "");
        }

        BigDecimal valor;
        try {
            valor = new BigDecimal(limpio);
        } catch (NumberFormatException e) {
            throw new ValorInvalidoException("El monto '" + texto + "' no es un numero");
        }

        valor = valor.setScale(2, RoundingMode.HALF_UP);
        if (valor.signum() <= 0) {
            throw new ValorInvalidoException("El monto debe ser mayor a cero (vino '" + texto + "')");
        }
        if (valor.compareTo(MONTO_MAXIMO) > 0) {
            throw new ValorInvalidoException("El monto '" + texto + "' es demasiado grande");
        }
        return valor;
    }

    static int plazoDias(String texto) {
        try {
            // "15" o "15.0" (Excel) o "15 dias"
            int dias = new BigDecimal(texto.replaceAll("[^0-9.]", "")).intValueExact();
            if (dias < 0 || dias > 365) {
                throw new ValorInvalidoException("El plazo '" + texto + "' debe estar entre 0 y 365 dias");
            }
            return dias;
        } catch (NumberFormatException | ArithmeticException e) {
            throw new ValorInvalidoException("El plazo '" + texto + "' no es un numero entero de dias");
        }
    }

    /** null = credito (si la columna no viene, todo se toma como credito). */
    static TipoVenta tipoVenta(String texto) {
        if (texto == null) {
            return TipoVenta.CREDITO;
        }
        String t = ColumnaImportacion.normalizar(texto);
        if (t.contains("contado") || t.equals("efectivo") || t.equals("co") || t.equals("cont")) {
            return TipoVenta.CONTADO;
        }
        if (t.contains("credito") || t.equals("cr") || t.equals("cred") || t.equals("c")) {
            return TipoVenta.CREDITO;
        }
        throw new ValorInvalidoException("Tipo de venta '" + texto + "' desconocido: se espera CREDITO o CONTADO");
    }

    /**
     * Lleva el telefono a E.164. Un numero mexicano de 10 digitos se vuelve
     * +52XXXXXXXXXX. Regresa null si no se puede: un telefono mal escrito no
     * debe tumbar la nota, solo se queda sin WhatsApp.
     */
    static String telefonoE164(String texto) {
        if (texto == null) {
            return null;
        }
        boolean conMas = texto.trim().startsWith("+");
        String digitos = texto.replaceAll("\\D", "");

        String e164;
        if (conMas) {
            e164 = "+" + digitos;
        } else if (digitos.length() == 10) {
            e164 = "+52" + digitos;
        } else if (digitos.length() == 12 && digitos.startsWith("52")) {
            e164 = "+" + digitos;
        } else if (digitos.length() == 13 && digitos.startsWith("521")) {
            // Formato viejo de celulares en Mexico (+52 1 ...): el 1 ya no se usa
            e164 = "+52" + digitos.substring(3);
        } else {
            return null;
        }
        return E164.matcher(e164).matches() ? e164 : null;
    }

    /** Recorta sin tronar: un nombre de 200 letras se guarda con 150. */
    static String recortar(String texto, int maximo) {
        if (texto == null) {
            return null;
        }
        String t = texto.trim();
        return t.length() <= maximo ? t : t.substring(0, maximo).trim();
    }

    static class ValorInvalidoException extends RuntimeException {
        ValorInvalidoException(String mensaje) {
            super(mensaje);
        }
    }
}
