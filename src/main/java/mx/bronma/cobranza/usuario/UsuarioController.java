package mx.bronma.cobranza.usuario;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/usuarios")
public class UsuarioController {

    private final UsuarioService service;

    public UsuarioController(UsuarioService service){
        this.service=service;
    }

    @GetMapping
    public List<UsuarioDto> listar(@RequestParam(required = false) RolUsuario rol) {
        return service.listar(rol);
    }
}
