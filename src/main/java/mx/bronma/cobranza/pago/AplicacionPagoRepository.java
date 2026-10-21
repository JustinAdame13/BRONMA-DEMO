package mx.bronma.cobranza.pago;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AplicacionPagoRepository extends JpaRepository<AplicacionPago, Long> {

    List<AplicacionPago> findByNotaId(Long notaId);
}
