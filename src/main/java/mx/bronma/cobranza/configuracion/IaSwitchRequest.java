package mx.bronma.cobranza.configuracion;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import mx.bronma.cobranza.Enums.*;

public record IaSwitchRequest(

        @NotNull(message = "proveedorIa es obligatorio")
        Enums.ProveedorIA proveedorIa,

        /**
         * Opcional: el modelo exacto del proveedor (ej. "gpt-4o-mini").
         * Si viene null o vacio se usa el modelo del .env / el por defecto del cliente.
         * La columna modelo_ia es VARCHAR(80).
         */
        @Size(max = 80, message = "modeloIa no puede pasar de 80 caracteres")
        String modeloIa
) {}
