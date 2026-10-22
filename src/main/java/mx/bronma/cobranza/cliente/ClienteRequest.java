package mx.bronma.cobranza.cliente;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Cuerpo del POST /clientes y del PUT /clientes/{id}.
 * La ubicacion NO va aqui: tiene su propio endpoint.
 */
public record ClienteRequest(

        @NotBlank @Size(max = 30) String codigoOrigen,

        @NotBlank @Size(max = 150) String nombreNegocio,

        @Size(max = 120) String contacto,

        /** E.164: + y de 8 a 15 digitos. Mismo patron que el CHECK de V1. */
        @Pattern(regexp = "^\\+[1-9][0-9]{7,14}$",
                 message = "debe venir en formato E.164, por ejemplo +528711234567")
        @Size(max = 16) String telefonoWhatsapp,

        @Size(max = 255) String direccion,

        Long rutaId,

        /** 0 = usar el limite por defecto de configuracion. */
        @DecimalMin("0") BigDecimal limiteCredito,

        /** null = usar el plazo por defecto de configuracion. */
        @Min(1) Integer plazoDias,

        @NotNull EstadoCredito estadoCredito,

        @NotNull Boolean aceptaWhatsapp,

        /**
         * Opcional. false = dar de baja (no hay DELETE). null = en el alta queda
         * activo y en la edicion no cambia.
         */
        Boolean activo) {
}
