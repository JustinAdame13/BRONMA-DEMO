package mx.bronma.cobranza.recordatorio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Llena la plantilla de la politica: {cliente}, {folio}, {saldo} y {vencimiento}.
 * El signo de pesos lo pone aqui ("$1,250.00") porque en la migracion no se
 * puede escribir pegado a la llave (Flyway lo toma como placeholder).
 */
public final class PlantillaMensaje {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private PlantillaMensaje() {
    }

    public static String llenar(String plantilla, String cliente, String folio, BigDecimal saldo,
                                LocalDate vencimiento) {
        return plantilla
                .replace("{cliente}", cliente == null ? "" : cliente)
                .replace("{folio}", folio == null ? "" : folio)
                .replace("{saldo}", saldo == null ? "" : String.format(Locale.ROOT, "$%,.2f", saldo))
                .replace("{vencimiento}", vencimiento == null ? "" : vencimiento.format(FECHA));
    }
}
