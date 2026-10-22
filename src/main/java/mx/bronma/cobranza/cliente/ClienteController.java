package mx.bronma.cobranza.cliente;

import jakarta.validation.Valid;
import mx.bronma.cobranza.common.Pagina;
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

@RestController
@RequestMapping("/api/v1/clientes")
public class ClienteController {

    private final ClienteService service;

    public ClienteController(ClienteService service) {
        this.service = service;
    }

    /** Paginado. Los cuatro filtros son opcionales y se combinan entre si. */
    @GetMapping
    public Pagina<ClienteResumenDto> listar(
            @RequestParam(required = false) Long rutaId,
            @RequestParam(required = false) EstadoCredito estadoCredito,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean sinUbicacion,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {

        return service.listar(rutaId, estadoCredito, q, sinUbicacion, pagina, tamano);
    }

    @GetMapping("/{id}")
    public ClienteDetalleDto obtener(@PathVariable Long id) {
        return service.obtener(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClienteDetalleDto crear(@Valid @RequestBody ClienteRequest req) {
        return service.crear(req);
    }

    @PutMapping("/{id}")
    public ClienteDetalleDto actualizar(@PathVariable Long id,
                                        @Valid @RequestBody ClienteRequest req) {
        return service.actualizar(id, req);
    }

    /** La oficina manda MANUAL (clic en el mapa); el celular del repartidor, GPS. */
    @PatchMapping("/{id}/ubicacion")
    public UbicacionDto fijarUbicacion(@PathVariable Long id,
                                       @Valid @RequestBody UbicacionRequest req) {
        return service.fijarUbicacion(id, req);
    }
}
