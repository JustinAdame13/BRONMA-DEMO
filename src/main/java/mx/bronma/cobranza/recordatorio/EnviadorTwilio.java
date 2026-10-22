package mx.bronma.cobranza.recordatorio;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

/**
 * WhatsApp por Twilio (sandbox para la demo). Usa la API REST directo, sin el
 * SDK de Twilio: es un solo POST.
 *
 * Variables: TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN y TWILIO_WHATSAPP_FROM
 * (default el numero del sandbox, whatsapp:+14155238886). En el sandbox cada
 * celular de destino tiene que unirse primero mandando "join &lt;palabra&gt;".
 */
@Component
@ConditionalOnProperty(name = "app.whatsapp.proveedor", havingValue = "twilio")
public class EnviadorTwilio implements EnviadorMensajes {

    private final RestClient http;
    private final String accountSid;
    private final String from;
    private final boolean mexicoConUno;

    public EnviadorTwilio(@Value("${app.twilio.account-sid:}") String accountSid,
                          @Value("${app.twilio.auth-token:}") String authToken,
                          @Value("${app.twilio.whatsapp-from:whatsapp:+14155238886}") String from,
                          @Value("${app.whatsapp.mx-agregar-1:false}") boolean mexicoConUno) {

        if (accountSid.isBlank() || authToken.isBlank()) {
            // Mejor tronar al arrancar que fallar en silencio cada manana
            throw new IllegalStateException(
                    "WHATSAPP_PROVEEDOR=twilio necesita TWILIO_ACCOUNT_SID y TWILIO_AUTH_TOKEN");
        }
        String sid = accountSid.trim();
        String token = authToken.trim();
        this.accountSid = sid;
        this.from = from.startsWith("whatsapp:") ? from : "whatsapp:" + from;
        this.mexicoConUno = mexicoConUno;

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
        factory.setReadTimeout(Duration.ofSeconds(20));

        this.http = RestClient.builder()
                .baseUrl("https://api.twilio.com")
                .requestFactory(factory)
                .defaultHeaders(h -> h.setBasicAuth(sid, token))
                .build();
    }

    @Override
    public String nombre() {
        return "twilio";
    }

    @Override
    public ResultadoEnvio enviar(String telefonoE164, String mensaje) {

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("From", from);
        form.add("To", "whatsapp:" + destino(telefonoE164));
        form.add("Body", mensaje);

        try {
            Map<?, ?> respuesta = http.post()
                    .uri("/2010-04-01/Accounts/{sid}/Messages.json", accountSid)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
            Object sid = respuesta == null ? null : respuesta.get("sid");
            return sid == null
                    ? ResultadoEnvio.fallo("Twilio respondio sin sid")
                    : ResultadoEnvio.exito(sid.toString());
        } catch (HttpStatusCodeException e) {
            return ResultadoEnvio.fallo("Twilio " + e.getStatusCode().value() + ": " + e.getResponseBodyAsString());
        } catch (RestClientException e) {
            return ResultadoEnvio.fallo("Sin respuesta de Twilio: " + e.getMessage());
        }
    }

    /**
     * WhatsApp identifico por anos los celulares de Mexico como +52 1 XXX.
     * Si los mensajes no llegan con +52XXXXXXXXXX, prueba WHATSAPP_MX_AGREGAR_1=true.
     */
    String destino(String telefonoE164) {
        if (mexicoConUno && telefonoE164.startsWith("+52") && telefonoE164.length() == 13) {
            return "+521" + telefonoE164.substring(3);
        }
        return telefonoE164;
    }
}
