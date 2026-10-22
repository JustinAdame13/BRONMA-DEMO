package mx.bronma.cobranza.cliente;

import java.time.Instant;

/** Respuesta del PATCH /clientes/{id}/ubicacion. */
public record UbicacionDto(
        Long id,
        Double latitud,
        Double longitud,
        UbicacionFuente ubicacionFuente,
        Instant ubicacionFijadaEn) {

    public static UbicacionDto from(Cliente c) {
        return new UbicacionDto(
                c.getId(),
                c.getLatitud(),
                c.getLongitud(),
                c.getUbicacionFuente(),
                c.getUbicacionFijadaEn());
    }
}
