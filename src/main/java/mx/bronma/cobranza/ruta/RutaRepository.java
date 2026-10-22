package mx.bronma.cobranza.ruta;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RutaRepository extends JpaRepository<Ruta, Long> {

    Optional<Ruta> findByCodigoOrigen(String codigoOrigen);

    List<Ruta> findByRepartidorIdAndActivoTrue(Long repartidorId);

    @Query("select r from Ruta r left join fetch r.repartidor")
    List<Ruta> listarConRepartidor();

    @Query("select r from Ruta r left join fetch r.repartidor where r.activo = :activo")
    List<Ruta> listarConRepartidor(@Param("activo") boolean activo);
}
