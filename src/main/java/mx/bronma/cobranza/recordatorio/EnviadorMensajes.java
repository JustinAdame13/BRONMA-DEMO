package mx.bronma.cobranza.recordatorio;

/**
 * Quien manda el WhatsApp. La logica de recordatorios solo conoce esta
 * interfaz: cambiar del sandbox de Twilio a WhatsApp Business es otra
 * implementacion, sin tocar RecordatorioService.
 *
 * Se elige con la variable WHATSAPP_PROVEEDOR: consola (default) o twilio.
 */
public interface EnviadorMensajes {

    /** Para el resumen de ejecucion y los logs. */
    String nombre();

    /**
     * Nunca lanza excepcion: un envio fallido regresa ok = false con el error.
     *
     * @param telefonoE164 destino, por ejemplo +528711234567.
     */
    ResultadoEnvio enviar(String telefonoE164, String mensaje);

    /** @param idProveedor SID de Twilio o id de WhatsApp Business (null si fallo). */
    record ResultadoEnvio(boolean ok, String idProveedor, String error) {

        public static ResultadoEnvio exito(String id) {
            return new ResultadoEnvio(true, id, null);
        }

        public static ResultadoEnvio fallo(String error) {
            return new ResultadoEnvio(false, null, error);
        }
    }
}
