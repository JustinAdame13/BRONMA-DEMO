package mx.bronma.cobranza.riesgo;

import mx.bronma.cobranza.common.Pagina;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Ayax: riesgo de impago por cliente. */
@RestController
@RequestMapping("/api/v1/riesgo")
public class RiesgoController {

    private final RiesgoService service;

    public RiesgoController(RiesgoService service) {
        this.service = service;
    }

    /**
     * Recalcula ahora (tambien corre solo todos los dias a las 6:15 am).
     * Sin clienteId evalua a todos. usarIa=false fuerza solo el modelo BASE.
     */
    @PostMapping("/evaluar")
    public RiesgoService.ResumenEvaluacion evaluar(
            @RequestParam(required = false) Long clienteId,
            @RequestParam(defaultValue = "true") boolean usarIa) {
        return service.evaluar(clienteId, usarIa);
    }

    /** La evaluacion vigente de cada cliente, el mas riesgoso primero. */
    @GetMapping
    public Pagina<EvaluacionDto> vigentes(
            @RequestParam(required = false) NivelRiesgo nivel,
            @RequestParam(required = false) Long rutaId,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return service.vigentes(nivel, rutaId, pagina, tamano);
    }

    /** Historial de un cliente (sirve para ver si va mejorando o empeorando). */
    @GetMapping("/clientes/{clienteId}")
    public List<EvaluacionDto> historial(@PathVariable Long clienteId) {
        return service.historial(clienteId);
    }
}
