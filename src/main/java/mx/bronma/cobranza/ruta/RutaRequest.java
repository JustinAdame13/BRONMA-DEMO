package mx.bronma.cobranza.ruta;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RutaRequest (
         @NotBlank  @Size(max = 30) String codigoOrigen,
         @NotBlank  @Size(max = 100)String nombre,
         @NotBlank @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String color,
          Long  repartidorId,
         @NotNull Boolean activo
){
}
