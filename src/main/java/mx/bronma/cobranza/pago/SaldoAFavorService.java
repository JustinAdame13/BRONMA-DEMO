package mx.bronma.cobranza.pago;

import mx.bronma.cobranza.nota.EstadoNota;
import mx.bronma.cobranza.nota.Nota;
import mx.bronma.cobranza.nota.NotaRepository;
import mx.bronma.cobranza.recordatorio.RecordatorioRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Saldo a favor del cliente.
 *
 * No hay tabla aparte: el saldo a favor es la parte de cada pago que no se ha
 * aplicado a ninguna nota (pagos.monto - suma de sus aplicaciones_pago). Asi
 * siempre se sabe de que pago salio cada peso y no hay un saldo suelto que se
 * pueda descuadrar.
 *
 * Regla: un cliente no puede tener saldo a favor y notas con saldo al mismo
 * tiempo. En cuanto aparece una nota (importacion, captura manual) o una nota
 * vuelve a deber (se revierte un pago), {@link #aplicar} usa el saldo a favor
 * para abonarla, primero el pago mas viejo y la nota que vence antes.
 */
@Service
public class SaldoAFavorService {

    /** Remanente por pago. Lo comparten varias consultas. */
    public static final String REMANENTE_POR_PAGO = """
            SELECT p.id, p.cliente_id, p.monto - COALESCE(SUM(a.monto), 0) AS remanente
            FROM pagos p
            LEFT JOIN aplicaciones_pago a ON a.pago_id = p.id
            GROUP BY p.id, p.cliente_id, p.monto
            HAVING p.monto - COALESCE(SUM(a.monto), 0) > 0
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final NotaRepository notas;
    private final PagoRepository pagos;
    private final RecordatorioRepository recordatorios;

    public SaldoAFavorService(NamedParameterJdbcTemplate jdbc, NotaRepository notas, PagoRepository pagos,
                              RecordatorioRepository recordatorios) {
        this.jdbc = jdbc;
        this.notas = notas;
        this.pagos = pagos;
        this.recordatorios = recordatorios;
    }

    /** Lo que el cliente tiene a favor hoy. */
    @Transactional(readOnly = true)
    public BigDecimal de(Long clienteId) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(remanente), 0) FROM (" + REMANENTE_POR_PAGO
                        + ") r WHERE r.cliente_id = :id",
                new MapSqlParameterSource("id", clienteId), BigDecimal.class);
    }

    /** De estos clientes, cuales tienen saldo a favor (para no llamar a aplicar() de mas). */
    @Transactional(readOnly = true)
    public Set<Long> conSaldoAFavor(Collection<Long> clienteIds) {
        if (clienteIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(jdbc.queryForList("SELECT DISTINCT cliente_id FROM (" + REMANENTE_POR_PAGO
                        + ") r WHERE r.cliente_id IN (:ids)",
                new MapSqlParameterSource("ids", clienteIds), Long.class));
    }

    /**
     * Usa el saldo a favor del cliente para abonar sus notas con saldo.
     * Debe correr dentro de la transaccion de quien lo llama (importacion,
     * nota, pago): bloquea las notas del cliente igual que un pago, asi que no
     * se cruza con un cobro simultaneo.
     *
     * @return cuanto se aplico (0 si no tenia saldo a favor o no debe nada).
     */
    @Transactional
    public BigDecimal aplicar(Long clienteId) {

        // Mismo bloqueo que PagoService.registrar: serializa los movimientos de dinero del cliente
        List<Nota> vivas = notas.vivasDelClienteParaPago(clienteId);
        if (vivas.isEmpty()) {
            return BigDecimal.ZERO;
        }

        List<Long> idsConRemanente = jdbc.queryForList("SELECT id FROM (" + REMANENTE_POR_PAGO
                        + ") r WHERE r.cliente_id = :id",
                new MapSqlParameterSource("id", clienteId), Long.class);
        if (idsConRemanente.isEmpty()) {
            return BigDecimal.ZERO;
        }

        // El pago mas viejo se usa primero
        List<Pago> conRemanente = new ArrayList<>(pagos.findAllById(idsConRemanente));
        conRemanente.sort(Comparator.comparing(Pago::getFechaPago).thenComparing(Pago::getId));

        BigDecimal total = BigDecimal.ZERO;
        List<Long> liquidadas = new ArrayList<>();
        int i = 0;

        for (Pago pago : conRemanente) {
            BigDecimal remanente = remanente(pago);

            while (remanente.signum() > 0 && i < vivas.size()) {
                Nota nota = vivas.get(i);
                BigDecimal abono = remanente.min(nota.getSaldo());

                nota.abonar(abono);
                sumarAplicacion(pago, nota, abono);
                remanente = remanente.subtract(abono);
                total = total.add(abono);

                if (nota.getEstado() == EstadoNota.PAGADA) {
                    liquidadas.add(nota.getId());
                    i++;
                }
            }
            if (i >= vivas.size()) {
                break;
            }
        }

        pagos.flush();
        if (!liquidadas.isEmpty()) {
            recordatorios.cancelarPendientes(liquidadas);
        }
        return total;
    }

    /** monto - lo ya aplicado. */
    static BigDecimal remanente(Pago pago) {
        BigDecimal aplicado = pago.getAplicaciones().stream()
                .map(AplicacionPago::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return pago.getMonto().subtract(aplicado);
    }

    /**
     * Si el pago ya tenia una aplicacion a esa nota (por ejemplo, la nota se
     * reabrio al revertir otro pago) se suma ahi: la tabla tiene UNIQUE (pago, nota).
     */
    private static void sumarAplicacion(Pago pago, Nota nota, BigDecimal abono) {
        for (AplicacionPago a : pago.getAplicaciones()) {
            if (a.getNota().getId().equals(nota.getId())) {
                a.setMonto(a.getMonto().add(abono));
                return;
            }
        }
        AplicacionPago a = new AplicacionPago();
        a.setPago(pago);
        a.setNota(nota);
        a.setMonto(abono);
        pago.getAplicaciones().add(a);
    }
}
