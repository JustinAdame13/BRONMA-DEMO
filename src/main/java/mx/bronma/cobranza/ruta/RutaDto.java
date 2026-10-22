package mx.bronma.cobranza.ruta;

import jakarta.annotation.Nullable;

public record RutaDto(
        Long id,
        String codigoOrigen,
        String nombre,
        @Nullable RepartidorDto repartidor,
        Boolean activo,
        String color,
        Long totalClientes
) {

    public static RutaDto from(Ruta r, Long totalClientes){
        return new RutaDto(r.getId(),r.getCodigoOrigen(),r.getNombre(), RepartidorDto.from(r.getRepartidor()),
                r.getActivo(), r.getColor(),totalClientes);
    }
}
