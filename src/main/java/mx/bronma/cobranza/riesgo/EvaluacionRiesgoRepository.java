package mx.bronma.cobranza.riesgo;

import mx.bronma.cobranza.Enums.Enums;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EvaluacionRiesgoRepository extends JpaRepository<EvaluacionRiesgo, Long> {

    /** Para el upsert: si ya existe la de hoy, se actualiza en lugar de insertar. */
    Optional<EvaluacionRiesgo> findByClienteIdAndFechaCalculoAndModelo(
            Long clienteId, LocalDate fechaCalculo, Enums.ModeloAyax modelo);

    List<EvaluacionRiesgo> findByFechaCalculoAndModeloOrderByScoreDesc(
            LocalDate fechaCalculo, Enums.ModeloAyax modelo);

    /** Historial de un cliente, la mas reciente primero. */
    List<EvaluacionRiesgo> findByClienteIdOrderByFechaCalculoDesc(Long clienteId);
}
