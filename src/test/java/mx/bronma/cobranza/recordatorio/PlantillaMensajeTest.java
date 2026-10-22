package mx.bronma.cobranza.recordatorio;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlantillaMensajeTest {

    @Test
    void llenaTodasLasLlaves() {
        String texto = PlantillaMensaje.llenar(
                "Hola {cliente}, su nota {folio} por {saldo} vence el {vencimiento}.",
                "Abarrotes La Pena", "N-1001", new BigDecimal("1250.5"), LocalDate.of(2026, 10, 25));
        assertEquals("Hola Abarrotes La Pena, su nota N-1001 por $1,250.50 vence el 25/10/2026.", texto);
    }
}
