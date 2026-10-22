package mx.bronma.cobranza.recordatorio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Para desarrollo: no manda nada, solo lo escribe en el log. Es el default. */
@Component
@ConditionalOnProperty(name = "app.whatsapp.proveedor", havingValue = "consola", matchIfMissing = true)
public class EnviadorConsola implements EnviadorMensajes {

    private static final Logger log = LoggerFactory.getLogger(EnviadorConsola.class);

    @Override
    public String nombre() {
        return "consola";
    }

    @Override
    public ResultadoEnvio enviar(String telefonoE164, String mensaje) {
        log.info("[WhatsApp simulado] a {}: {}", telefonoE164, mensaje);
        return ResultadoEnvio.exito("consola-" + UUID.randomUUID());
    }
}
