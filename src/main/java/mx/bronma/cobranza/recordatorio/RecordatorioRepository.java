package mx.bronma.cobranza.recordatorio;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface RecordatorioRepository extends JpaRepository<Recordatorio, Long> {

    /** Lo que el scheduler tiene que mandar hoy (incluye atrasados si un dia no corrio). */
    List<Recordatorio> findByEstadoAndProgramadoParaLessThanEqualOrderByProgramadoParaAsc(
            EstadoRecordatorio estado, LocalDate hoy);

    boolean existsByNotaIdAndPoliticaId(Long notaId, Long politicaId);

    List<Recordatorio> findByNotaIdOrderByProgramadoParaAsc(Long notaId);
}
