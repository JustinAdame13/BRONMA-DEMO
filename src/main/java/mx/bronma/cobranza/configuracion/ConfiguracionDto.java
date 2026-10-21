package mx.bronma.cobranza.configuracion;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import mx.bronma.cobranza.Enums.*;

import java.math.BigDecimal;
import java.time.LocalTime;

public record ConfiguracionDto(
        @NotBlank @Size(max = 120) String nombreEmpresa,
        @NotBlank @Size(max = 40) String zonaHoraria,
        @NotNull @Min(1) Integer plazoCreditoDefaultDias,
        @NotNull @DecimalMin("0") BigDecimal limiteCreditoDefault,
        @NotNull Boolean whatsappHabilitado,
        @NotNull LocalTime horaEnvioRecordatorios,

        /** Solo lectura en el PUT. Se cambia con PATCH /configuracion/ia */
        Enums.ProveedorIA proveedorIa,

        /** Solo lectura en el PUT. Null significa usar el modelo por defecto del proveedor */
        String modeloIa,

        /** Solo lectura: el proveedor elegido tiene API key y esta operando. */
        Boolean iaActiva
) {

    public static ConfiguracionDto from(Configuracion c, boolean iaActiva) {
        return new ConfiguracionDto(
                c.getNombreEmpresa(), c.getZonaHoraria(),
                c.getPlazoCreditoDefaultDias(), c.getLimiteCreditoDefault(),
                c.getWhatsappHabilitado(), c.getHoraEnvioRecordatorios(),
                c.getProveedorIa(), c.getModeloIa(), iaActiva);
    }
}
