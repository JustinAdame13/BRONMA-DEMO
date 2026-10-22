package mx.bronma.cobranza.ruta;

import mx.bronma.cobranza.usuario.Usuario;

public record RepartidorDto(Long id, String nombre){
    public static RepartidorDto from(Usuario u){
        return (u == null) ? null : new RepartidorDto(u.getId(), u.getNombre());
    }
}
