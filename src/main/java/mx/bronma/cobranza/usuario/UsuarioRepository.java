package mx.bronma.cobranza.usuario;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    List<Usuario> findByRolAndActivoTrue(RolUsuario rol);
}
