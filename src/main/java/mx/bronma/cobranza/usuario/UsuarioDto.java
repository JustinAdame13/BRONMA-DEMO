package mx.bronma.cobranza.usuario;


public record UsuarioDto(
         Long id,
         String nombre,
         String telefono,
         RolUsuario rol,
         Boolean activo){

        public static UsuarioDto from(Usuario u){
            return new UsuarioDto(u.getId(),u.getNombre(),u.getTelefono(),u.getRol(),u.getActivo());
        }
}
