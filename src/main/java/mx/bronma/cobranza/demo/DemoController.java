package mx.bronma.cobranza.demo;

import mx.bronma.cobranza.common.ReglaNegocioException;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Solo con el perfil demo. En produccion esta ruta no existe (404). */
@RestController
@Profile("demo")
@RequestMapping("/api/v1/demo")
public class DemoController {

    private final DemoService service;

    public DemoController(DemoService service) {
        this.service = service;
    }

    /**
     * Borra clientes, rutas, usuarios, notas, pagos, recordatorios y evaluaciones,
     * y vuelve a cargar la demo con fechas de hoy. Pide ?confirmar=SI para que
     * no se dispare por accidente. Tarda unos segundos.
     */
    @PostMapping("/reiniciar")
    public DemoService.ResumenDemo reiniciar(@RequestParam(defaultValue = "") String confirmar) {
        if (!"SI".equals(confirmar)) {
            throw new ReglaNegocioException("Esto borra todos los datos. Manda ?confirmar=SI");
        }
        service.borrar();
        return service.cargar();
    }
}
