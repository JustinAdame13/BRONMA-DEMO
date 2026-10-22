package mx.bronma.cobranza.recordatorio;

import jakarta.validation.Valid;
import mx.bronma.cobranza.common.Pagina;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/recordatorios")
public class RecordatorioController {

    private final RecordatorioService service;

    public RecordatorioController(RecordatorioService service) {
        this.service = service;
    }

    /** Paginado, el mas reciente primero. fecha = programado para ese dia (aaaa-mm-dd). */
    @GetMapping
    public Pagina<RecordatorioDto> listar(
            @RequestParam(required = false) EstadoRecordatorio estado,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(required = false) Long clienteId,
            @RequestParam(required = false) Long notaId,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return service.listar(estado, fecha, clienteId, notaId, pagina, tamano);
    }

    /** Dispara la corrida a mano (en la demo). 409 si ya hay una en curso. */
    @PostMapping("/ejecutar")
    public ResumenEjecucion ejecutar() {
        return service.ejecutar();
    }

    @GetMapping("/politicas")
    public List<PoliticaDto> politicas() {
        return service.politicas();
    }

    /** 409 si ya existe una regla con esos dias. */
    @PostMapping("/politicas")
    @ResponseStatus(HttpStatus.CREATED)
    public PoliticaDto crearPolitica(@Valid @RequestBody PoliticaDto dto) {
        return service.crearPolitica(dto);
    }

    /** No hay DELETE: una regla se desactiva con activo = false. */
    @PutMapping("/politicas/{id}")
    public PoliticaDto actualizarPolitica(@PathVariable Long id, @Valid @RequestBody PoliticaDto dto) {
        return service.actualizarPolitica(id, dto);
    }
}
