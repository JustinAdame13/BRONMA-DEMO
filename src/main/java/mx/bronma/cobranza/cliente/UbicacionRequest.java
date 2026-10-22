package mx.bronma.cobranza.cliente;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;

/**
 * Cuerpo del PATCH /clientes/{id}/ubicacion.
 * Las tres primeras van juntas o las tres en null (null = borrar la ubicacion).
 */
public record UbicacionRequest(

        @DecimalMin("-90") @DecimalMax("90")
        Double latitud,

        @DecimalMin("-180") @DecimalMax("180")
        Double longitud,

        UbicacionFuente fuente,

        /** Opcional, solo para validar. No se guarda en la base. */
        @Min(0) Integer precisionMetros) {
}
