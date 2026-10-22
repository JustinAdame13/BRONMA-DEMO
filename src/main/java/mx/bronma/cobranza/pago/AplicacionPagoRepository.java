package mx.bronma.cobranza.pago;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface AplicacionPagoRepository extends JpaRepository<AplicacionPago, Long> {

    List<AplicacionPago> findByNotaId(Long notaId);

    /**
     * De estas notas, cuales ya recibieron algun abono.
     * La importacion no puede cambiar el monto ni el cliente de esas notas.
     */
    @Query("select distinct a.nota.id from AplicacionPago a where a.nota.id in :notaIds")
    Set<Long> notasConPagos(@Param("notaIds") Collection<Long> notaIds);
}
