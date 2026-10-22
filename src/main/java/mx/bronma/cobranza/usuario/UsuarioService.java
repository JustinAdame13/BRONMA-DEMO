package mx.bronma.cobranza.usuario;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UsuarioService {

    private final UsuarioRepository repository;

    public UsuarioService(UsuarioRepository repository){
        this.repository=repository;
    }

    @Transactional(readOnly = true)
    public List<UsuarioDto> listar(RolUsuario rol){

        List<Usuario> usuarios=(rol==null)? repository.findAllByOrderByNombreAsc():repository.findByRolOrderByNombreAsc(rol);

        return usuarios.stream().map(UsuarioDto::from).toList();
    }
}
