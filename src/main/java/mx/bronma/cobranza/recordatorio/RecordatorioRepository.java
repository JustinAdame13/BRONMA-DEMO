package mx.bronma.cobranza.recordatorio;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface RecordatorioRepository extends JpaRepository<Recordatorio, Long> {

    /** Lo que el scheduler tiene que mandar hoy (incluye atrasados si un dia no corrio). */
    List<Recordatorio> findByEstadoAndProgramadoParaLessThanEqualOrderByProgramadoParaAsc(
            EstadoRecordatorio estado, LocalDate hoy);

    boolean existsByNotaIdAndPoliticaId(Long notaId, Long politicaId);

    List<Recordatorio> findByNotaIdOrderByProgramadoParaAsc(Long notaId);

    /**
     * Nota pagada o cancelada: sus recordatorios que no han salido ya no aplican.
     * Lo llaman pagos y notas, para que a un cliente que ya pago no le llegue
     * un "tu nota vence manana".
     */
    @Modifying
    @Query("""
            update Recordatorio r set r.estado = mx.bronma.cobranza.recordatorio.EstadoRecordatorio.CANCELADO
            where r.nota.id in :notaIds
              and r.estado = mx.bronma.cobranza.recordatorio.EstadoRecordatorio.PENDIENTE
            """)
    int cancelarPendientes(@Param("notaIds") Collection<Long> notaIds);

    /**
     * Al revertir un pago la nota vuelve a deber: los recordatorios que se
     * cancelaron por ese pago y cuya fecha no ha pasado vuelven a PENDIENTE.
     * (Los de fechas pasadas se quedan cancelados: no tiene caso mandarlos tarde.)
     */
    @Modifying
    @Query("""
            update Recordatorio r set r.estado = mx.bronma.cobranza.recordatorio.EstadoRecordatorio.PENDIENTE
            where r.nota.id in :notaIds
              and r.estado = mx.bronma.cobranza.recordatorio.EstadoRecordatorio.CANCELADO
              and r.programadoPara >= :hoy
            """)
    int reactivarCancelados(@Param("notaIds") Collection<Long> notaIds, @Param("hoy") LocalDate hoy);
}
