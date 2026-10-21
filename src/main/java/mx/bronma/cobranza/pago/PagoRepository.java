package mx.bronma.cobranza.pago;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PagoRepository extends JpaRepository<Pago, Long> {

    /** Si el celular reintenta, se regresa el pago que ya existia en lugar de duplicarlo. */
    Optional<Pago> findByUuidDispositivo(UUID uuidDispositivo);

    List<Pago> findByClienteIdOrderByFechaPagoDesc(Long clienteId);

    List<Pago> findByFechaPagoBetween(Instant desde, Instant hasta);
}
