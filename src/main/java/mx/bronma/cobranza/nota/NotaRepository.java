package mx.bronma.cobranza.nota;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * Las notas vivas del cliente, BLOQUEADAS hasta que termine la transaccion
     * (SELECT ... FOR UPDATE). Si la oficina y el repartidor registran un pago
     * del mismo cliente al mismo tiempo, el segundo espera al primero y ve los
     * saldos ya actualizados: nadie abona dos veces sobre el mismo saldo.
     *
     * Orden: lo que vence antes se paga primero.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select n from Nota n
            where n.cliente.id = :clienteId
              and n.estado <> mx.bronma.cobranza.nota.EstadoNota.CANCELADA
              and n.saldo > 0
            order by n.fechaVencimiento, n.fechaEmision, n.id
            """)
    List<Nota> vivasDelClienteParaPago(@Param("clienteId") Long clienteId);

    /** Para revertir un pago: las notas que toco, bloqueadas. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from Nota n where n.id in :ids")
    List<Nota> bloquear(@Param("ids") Collection<Long> ids);
}
