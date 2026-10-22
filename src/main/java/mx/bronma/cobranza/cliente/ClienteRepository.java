package mx.bronma.cobranza.cliente;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    Optional<Cliente> findByCodigoOrigen(String codigoOrigen);

    /** Para importar un archivo completo con una sola consulta, no una por fila. */
    List<Cliente> findByCodigoOrigenIn(Collection<String> codigos);

    List<Cliente> findByRutaIdAndActivoTrue(Long rutaId);

    @Query("select c.ruta.id as rutaId, count(c) as total from Cliente c where c.ruta is not null group by c.ruta.id")
    List <ConteoPorRuta> contarClientesPorRuta();

    /** Para rearmar el RutaDto despues de guardar. */
    long countByRutaId(Long rutaId);
}
