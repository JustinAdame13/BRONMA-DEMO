package mx.bronma.cobranza.cobro;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/lista-cobro")
public class ListaCobroController {

    private final ListaCobroService service;

    public ListaCobroController(ListaCobroService service) {
        this.service = service;
    }

    /**
     * A quien cobrarle hoy. Por ruta (?rutaId=) para la oficina, o por
     * repartidor (?repartidorId=) para el celular: junta sus rutas activas.
     * orden=PRIORIDAD (default) o VISITA. diasPorVencer: incluye lo que vence
     * en esos dias para adelantar el cobro (default 3).
     */
    @GetMapping
    public ListaCobroDto generar(
            @RequestParam(required = false) Long rutaId,
            @RequestParam(required = false) Long repartidorId,
            @RequestParam(defaultValue = "PRIORIDAD") OrdenLista orden,
            @RequestParam(defaultValue = "3") int diasPorVencer) {
        return service.generar(rutaId, repartidorId, orden, diasPorVencer);
    }
}
