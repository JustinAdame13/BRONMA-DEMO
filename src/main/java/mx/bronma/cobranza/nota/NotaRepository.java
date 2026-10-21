package mx.bronma.cobranza.nota;

import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NotaRepository extends JpaRepository<Nota, Long> {

    Optional<Nota> findByFolioOrigen(String folioOrigen);

    /** Para importar un archivo completo con una sola consulta, no una por fila. */
    List<Nota> findByFolioOrigenIn(Collection<String> folios);

    /**
     * Notas con saldo de un cliente, la mas vieja primero.
     * Es el orden natural para repartir un pago (primero lo que vence antes).
     */
    List<Nota> findByClienteIdAndEstadoInAndSaldoGreaterThanOrderByFechaVencimientoAsc(
            Long clienteId, Collection<EstadoNota> estados, BigDecimal saldoMinimo);
}
