package mx.bronma.cobranza.importacion;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportacionRepository extends JpaRepository<Importacion, Long> {

    Page<Importacion> findAllByOrderByImportadoEnDesc(Pageable pageable);
}
