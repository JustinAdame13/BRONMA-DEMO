package mx.bronma.cobranza.ruta;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/rutas")
public class RutaController {

    private final RutaService service;

    public RutaController(RutaService service) {
        this.service = service;
    }

    /** Sin el parametro regresa todas; con activo=true o false, filtradas. */
    @GetMapping
    public List<RutaDto> listar(@RequestParam(required = false) Boolean activo) {
        return service.listar(activo);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RutaDto crear(@Valid @RequestBody RutaRequest req) {
        return service.crear(req);
    }

    /** Dar de baja una ruta = mandar activo en false. No hay DELETE. */
    @PutMapping("/{id}")
    public RutaDto actualizar(@PathVariable Long id, @Valid @RequestBody RutaRequest req) {
        return service.actualizar(id, req);
    }

    /** Renumera el orden de visita. Responde 204 sin cuerpo. */
    @PatchMapping("/{id}/orden")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reordenar(@PathVariable Long id, @Valid @RequestBody OrdenRequest req) {
        service.reordenar(id, req);
    }
}
