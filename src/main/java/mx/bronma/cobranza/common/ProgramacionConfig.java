package mx.bronma.cobranza.common;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Activa los @Scheduled: la evaluacion diaria de Ayax y el envio de
 * recordatorios. En Render gratis el servidor se duerme sin trafico y las
 * tareas no corren; para la demo estan POST /riesgo/evaluar y
 * POST /recordatorios/ejecutar.
 */
@Configuration
@EnableScheduling
public class ProgramacionConfig {
}
