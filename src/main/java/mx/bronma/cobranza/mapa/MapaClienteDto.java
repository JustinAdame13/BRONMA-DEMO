package mx.bronma.cobranza.mapa;

import mx.bronma.cobranza.cliente.EstadoCredito;
import mx.bronma.cobranza.riesgo.NivelRiesgo;

import java.math.BigDecimal;

/**
 * Cada pin de GET /mapa/clientes. Forma del contrato del roadmap (B4);
 * prioridad y montoACobrar son extras que no rompen el contrato.
 *
 * @param latitud      null = sin ubicacion (el front lo lista aparte para fijarla).
 * @param saldo        total de notas vivas (string decimal en el JSON).
 * @param saldoVencido saldo con vencimiento &lt; hoy.
 * @param diasAtrasoMax 0 si va al corriente.
 * @param nivelRiesgo  ultima evaluacion de Ayax; null si nunca se ha evaluado.
 * @param prioridad    0 a 100, la misma formula de la lista de cobro.
 */
public record MapaClienteDto(
        Long id,
        String nombreNegocio,
        Long rutaId,
        String rutaColor,
        Double latitud,
        Double longitud,
        Integer ordenVisita,
        BigDecimal saldo,
        BigDecimal saldoVencido,
        int diasAtrasoMax,
        NivelRiesgo nivelRiesgo,
        EstadoCredito estadoCredito,
        int prioridad,
        BigDecimal montoACobrar) {
}
