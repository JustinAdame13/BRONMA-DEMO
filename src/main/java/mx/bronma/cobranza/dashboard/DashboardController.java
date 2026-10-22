package mx.bronma.cobranza.dashboard;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    /**
     * Cartera de hoy (total, vencida, antiguedad, top deudores, por ruta, por
     * riesgo) y cobranza del periodo (cobrado, recuperado por recordatorios,
     * dias promedio de cobro). desde/hasta en aaaa-mm-dd; default ultimos 30 dias.
     */
    @GetMapping
    public DashboardDto generar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "10") int top) {
        return service.generar(desde, hasta, top);
    }
}
