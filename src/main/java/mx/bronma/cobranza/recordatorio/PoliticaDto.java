package mx.bronma.cobranza.recordatorio;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Una regla de la politica de recordatorios (respuesta y cuerpo de POST/PUT).
 *
 * @param diasOffset -3 = tres dias antes del vencimiento, 0 = el dia que vence, 7 = una semana tarde.
 * @param plantilla  texto con {cliente}, {folio}, {saldo} y {vencimiento}.
 * @param id         se ignora en POST/PUT (va en la URL).
 */
public record PoliticaDto(
        Long id,
        @NotNull @Min(-30) @Max(90) Integer diasOffset,
        @NotBlank @Size(max = 1000) String plantilla,
        @Size(max = 100) String nombrePlantillaWhatsapp,
        @NotNull Boolean activo) {

    public static PoliticaDto from(PoliticaRecordatorio p) {
        return new PoliticaDto(p.getId(), p.getDiasOffset(), p.getPlantilla(), p.getNombrePlantillaWhatsapp(),
                p.getActivo());
    }
}
