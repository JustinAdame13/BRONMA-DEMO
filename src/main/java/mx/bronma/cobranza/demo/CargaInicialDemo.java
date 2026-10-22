package mx.bronma.cobranza.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Con el perfil demo y la base vacia, carga la demo al arrancar. Si ya hay clientes, no toca nada. */
@Component
@Profile("demo")
public class CargaInicialDemo implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CargaInicialDemo.class);

    private final DemoService service;

    public CargaInicialDemo(DemoService service) {
        this.service = service;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (service.hayDatos()) {
            log.info("Perfil demo: la base ya tiene clientes, no se cargan datos de demo");
            return;
        }
        try {
            log.info("Perfil demo: base vacia, cargando datos de demo...");
            log.info("Datos de demo: {}", service.cargar());
        } catch (Exception e) {
            // Que la app arranque aunque la demo falle: se puede reintentar con POST /demo/reiniciar
            log.error("No se pudieron cargar los datos de demo", e);
        }
    }
}
