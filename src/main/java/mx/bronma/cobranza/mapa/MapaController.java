package mx.bronma.cobranza.mapa;

import mx.bronma.cobranza.cobro.CarteraCliente;
import mx.bronma.cobranza.cobro.CarteraConsultas;
import mx.bronma.cobranza.configuracion.ConfiguracionService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * B4: pines del mapa. Solo lectura, una consulta (clientes + saldos + ultima
 * evaluacion de Ayax con LATERAL), sin N+1.
 */
@RestController
@RequestMapping("/api/v1/mapa")
public class MapaController {

    private final CarteraConsultas cartera;
    private final ConfiguracionService configuracion;

    public MapaController(CarteraConsultas cartera, ConfiguracionService configuracion) {
        this.cartera = cartera;
        this.configuracion = configuracion;
    }

    /** Clientes activos (con o sin ubicacion). rutaId opcional. */
    @GetMapping("/clientes")
    @Transactional(readOnly = true)
    public List<MapaClienteDto> clientes(@RequestParam(required = false) Long rutaId) {
        return cartera.clientes(rutaId, null, configuracion.obtenerEntidad().hoy(), 0).stream()
                .map(MapaController::pin)
                .toList();
    }

    private static MapaClienteDto pin(CarteraCliente c) {
        return new MapaClienteDto(
                c.clienteId(),
                c.nombreNegocio(),
                c.rutaId(),
                c.rutaColor(),
                c.latitud(),
                c.longitud(),
                c.ordenVisita(),
                c.saldo(),
                c.saldoVencido(),
                c.diasAtrasoMax(),
                c.proximoVencimiento(),
                c.nivelRiesgo(),
                c.estadoCredito(),
                c.prioridad().puntos(),
                c.exigible());
    }
}
