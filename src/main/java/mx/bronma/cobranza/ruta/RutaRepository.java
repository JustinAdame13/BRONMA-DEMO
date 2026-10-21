package mx.bronma.cobranza.ruta;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RutaRepository extends JpaRepository<Ruta, Long> {

    Optional<Ruta> findByCodigoOrigen(String codigoOrigen);

    List<Ruta> findByRepartidorIdAndActivoTrue(Long repartidorId);
}
