package mx.bronma.cobranza.recordatorio;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PoliticaRecordatorioRepository extends JpaRepository<PoliticaRecordatorio, Long> {

    List<PoliticaRecordatorio> findByActivoTrueOrderByDiasOffsetAsc();
}
