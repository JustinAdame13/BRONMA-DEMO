package mx.bronma.cobranza.pago;

import mx.bronma.cobranza.cliente.Cliente;
import mx.bronma.cobranza.cliente.ClienteRepository;
import mx.bronma.cobranza.common.ConflictoException;
import mx.bronma.cobranza.common.Pagina;
import mx.bronma.cobranza.common.RecursoNoEncontradoException;
import mx.bronma.cobranza.common.ReglaNegocioException;
import mx.bronma.cobranza.configuracion.Configuracion;
import mx.bronma.cobranza.configuracion.ConfiguracionService;
import mx.bronma.cobranza.nota.EstadoNota;
import mx.bronma.cobranza.nota.Nota;
import mx.bronma.cobranza.nota.NotaRepository;
import mx.bronma.cobranza.pago.PagoRequest.AplicacionRequest;
import mx.bronma.cobranza.recordatorio.RecordatorioRepository;
import mx.bronma.cobranza.usuario.Usuario;
import mx.bronma.cobranza.usuario.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Registro de pagos y su reparto entre las notas del cliente.
 *
 * Reglas:
 * <ul>
 *   <li>Sin "aplicaciones" el pago se reparte solo: primero la nota que vence
 *       antes (la mas atrasada), hasta acabarse el dinero.</li>
 *   <li>Con "aplicaciones" la oficina decide a que notas va; la suma no puede
 *       pasar del monto del pago.</li>
 *   <li>Si las aplicaciones no suman todo el monto, lo que sobra se va a las
 *       demas notas del cliente (la que vence antes primero).</li>
 *   <li>Lo que sobra despues de cubrir toda la deuda queda como SALDO A FAVOR
 *       del cliente y se usa solo en cuanto tenga una nota nueva (ver
 *       {@link SaldoAFavorService}).</li>
 *   <li>Las notas del cliente se bloquean mientras se registra el pago: dos
 *       cobros simultaneos del mismo cliente no abonan sobre el mismo saldo.</li>
 *   <li>Mismo uuidDispositivo = el celular reintento: se regresa el pago que ya
 *       existe (200) en lugar de registrarlo dos veces.</li>
 *   <li>Una nota que queda PAGADA cancela sus recordatorios pendientes.</li>
 * </ul>
 */
@Service
public class PagoService {

    /** Tolerancia para relojes de celular adelantados. */
    private static final Duration TOLERANCIA_FUTURO = Duration.ofMinutes(10);

    private static final int TAMANO_MAXIMO = 100;

    private final PagoRepository repository;
    private final PagoConsultas consultas;
    private final NotaRepository notas;
    private final ClienteRepository clientes;
    private final UsuarioRepository usuarios;
    private final RecordatorioRepository recordatorios;
    private final ConfiguracionService configuracion;
    private final SaldoAFavorService saldoAFavor;

    public PagoService(PagoRepository repository,
                       PagoConsultas consultas,
                       NotaRepository notas,
                       ClienteRepository clientes,
                       UsuarioRepository usuarios,
                       RecordatorioRepository recordatorios,
                       ConfiguracionService configuracion,
                       SaldoAFavorService saldoAFavor) {
        this.repository = repository;
        this.consultas = consultas;
        this.notas = notas;
        this.clientes = clientes;
        this.usuarios = usuarios;
        this.recordatorios = recordatorios;
        this.configuracion = configuracion;
        this.saldoAFavor = saldoAFavor;
    }

    /** El pago y si se acaba de crear (201) o ya existia por un reintento (200). */
    public record Registro(PagoDto pago, boolean nuevo) {
    }

    // ------------------------------------------------------------------
    // Registrar
    // ------------------------------------------------------------------

    @Transactional
    public Registro registrar(PagoRequest req) {

        Cliente cliente = clientes.findById(req.clienteId())
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el cliente " + req.clienteId()));

        Usuario registradoPor = null;
        if (req.registradoPorId() != null) {
            registradoPor = usuarios.findById(req.registradoPorId())
                    .orElseThrow(() -> new RecursoNoEncontradoException("No existe el usuario " + req.registradoPorId()));
        }
        if (req.origen() == OrigenPago.RUTA && registradoPor == null) {
            throw new ReglaNegocioException("Un pago cobrado en ruta necesita registradoPorId (quien cobro)",
                    Map.of("registradoPorId", "obligatorio cuando origen = RUTA"));
        }

        // Siempre con centavos: 1000 -> 1000.00 (en mensajes y comparaciones)
        BigDecimal monto = req.monto().setScale(2, RoundingMode.HALF_UP);

        Instant ahora = Instant.now();
        Instant fechaPago = req.fechaPago() != null ? req.fechaPago() : ahora;
        if (fechaPago.isAfter(ahora.plus(TOLERANCIA_FUTURO))) {
            throw new ReglaNegocioException("La fecha del pago no puede estar en el futuro",
                    Map.of("fechaPago", "revisa la hora del celular"));
        }

        // Primero el bloqueo: desde aqui, otro pago de este cliente espera a que terminemos.
        List<Nota> vivas = notas.vivasDelClienteParaPago(cliente.getId());

        // Reintento del celular. Se revisa DESPUES del bloqueo para que dos envios
        // simultaneos del mismo cobro no pasen los dos.
        if (req.uuidDispositivo() != null) {
            Optional<Pago> previo = repository.findByUuidDispositivo(req.uuidDispositivo());
            if (previo.isPresent()) {
                Pago p = previo.get();
                if (!p.getCliente().getId().equals(cliente.getId()) || p.getMonto().compareTo(monto) != 0) {
                    throw new ConflictoException("El uuidDispositivo " + req.uuidDispositivo()
                            + " ya se uso en otro pago con datos distintos");
                }
                return new Registro(dto(p), false);
            }
        }

        Map<Nota, BigDecimal> reparto = (req.aplicaciones() == null || req.aplicaciones().isEmpty())
                ? repartirSolo(vivas, monto)
                : repartoIndicado(vivas, req.aplicaciones(), monto);

        Pago pago = new Pago();
        pago.setCliente(cliente);
        pago.setMonto(monto);
        pago.setFechaPago(fechaPago);
        pago.setMetodo(req.metodo());
        pago.setOrigen(req.origen());
        pago.setRegistradoPor(registradoPor);
        pago.setReferencia(vacioANull(req.referencia()));
        pago.setComentario(vacioANull(req.comentario()));
        pago.setUuidDispositivo(req.uuidDispositivo());

        List<Long> liquidadas = new ArrayList<>();
        for (Map.Entry<Nota, BigDecimal> e : reparto.entrySet()) {
            Nota nota = e.getKey();
            nota.abonar(e.getValue());
            if (nota.getEstado() == EstadoNota.PAGADA) {
                liquidadas.add(nota.getId());
            }

            AplicacionPago a = new AplicacionPago();
            a.setPago(pago);
            a.setNota(nota);
            a.setMonto(e.getValue());
            pago.getAplicaciones().add(a);
        }

        repository.saveAndFlush(pago); // cascade: guarda tambien las aplicaciones

        if (!liquidadas.isEmpty()) {
            recordatorios.cancelarPendientes(liquidadas);
        }

        // Si la oficina indico aplicaciones por menos del monto y el cliente aun debe
        // otras notas, lo que sobra se va a esas (la que vence antes primero).
        // Solo queda saldo a favor si de plano ya no debe nada.
        if (SaldoAFavorService.remanente(pago).signum() > 0) {
            saldoAFavor.aplicar(cliente.getId());
        }

        return new Registro(dto(pago), true);
    }

    /**
     * Primero lo que vence antes, hasta acabarse el dinero. Si el pago es mayor
     * a la deuda, lo que sobra no se aplica: queda como saldo a favor.
     */
    private Map<Nota, BigDecimal> repartirSolo(List<Nota> vivas, BigDecimal monto) {

        Map<Nota, BigDecimal> reparto = new LinkedHashMap<>();
        BigDecimal resta = monto;
        for (Nota n : vivas) {
            if (resta.signum() == 0) {
                break;
            }
            BigDecimal abono = resta.min(n.getSaldo());
            reparto.put(n, abono);
            resta = resta.subtract(abono);
        }
        return reparto;
    }

    /** La oficina dice a que notas va cada parte. Todo se valida antes de tocar nada. */
    private Map<Nota, BigDecimal> repartoIndicado(List<Nota> vivas, List<AplicacionRequest> aplicaciones,
                                                  BigDecimal monto) {

        Map<Long, Nota> porId = new HashMap<>();
        for (Nota n : vivas) {
            porId.put(n.getId(), n);
        }

        Map<Nota, BigDecimal> reparto = new LinkedHashMap<>();
        Set<Long> vistas = new HashSet<>();
        BigDecimal suma = BigDecimal.ZERO;

        for (AplicacionRequest a : aplicaciones) {
            BigDecimal abono = a.monto().setScale(2, RoundingMode.HALF_UP);
            if (!vistas.add(a.notaId())) {
                throw new ReglaNegocioException("La nota " + a.notaId() + " viene repetida en las aplicaciones");
            }
            Nota n = porId.get(a.notaId());
            if (n == null) {
                throw new ReglaNegocioException("La nota " + a.notaId()
                        + " no es de este cliente, ya esta pagada o esta cancelada");
            }
            if (abono.compareTo(n.getSaldo()) > 0) {
                throw new ReglaNegocioException("A la nota " + n.getFolioOrigen() + " se le quieren abonar $"
                        + abono + " pero solo debe $" + n.getSaldo());
            }
            reparto.put(n, abono);
            suma = suma.add(abono);
        }

        // Menos que el pago se vale (lo demas queda a favor); mas, no
        if (suma.compareTo(monto) > 0) {
            throw new ReglaNegocioException("Las aplicaciones suman $" + suma
                    + " y el pago es de $" + monto + "; no pueden pasar del pago");
        }
        return reparto;
    }

    // ------------------------------------------------------------------
    // Revertir
    // ------------------------------------------------------------------

    /**
     * Deshace un pago registrado por error: regresa los abonos a sus notas y
     * borra el pago (con el saldo a favor que hubiera dejado). Los recordatorios
     * que se habian cancelado y cuya fecha no ha pasado vuelven a quedar
     * pendientes. Si el cliente tiene saldo a favor de otros pagos, se usa para
     * cubrir las notas que se reabrieron.
     */
    @Transactional
    public void revertir(Long id) {
        Pago pago = repository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el pago " + id));

        List<Long> idsNotas = pago.getAplicaciones().stream().map(a -> a.getNota().getId()).toList();
        if (!idsNotas.isEmpty()) {
            notas.bloquear(idsNotas); // carga las notas con FOR UPDATE antes de tocar su saldo
        }

        List<Long> reabiertas = new ArrayList<>();
        for (AplicacionPago a : pago.getAplicaciones()) {
            Nota n = a.getNota();
            n.revertirAbono(a.getMonto());
            if (n.getEstado() != EstadoNota.CANCELADA) {
                reabiertas.add(n.getId());
            }
        }

        Long clienteId = pago.getCliente().getId();
        repository.delete(pago);
        repository.flush();

        if (!reabiertas.isEmpty()) {
            recordatorios.reactivarCancelados(reabiertas, hoy());
            saldoAFavor.aplicar(clienteId);
        }
    }

    // ------------------------------------------------------------------
    // Lectura
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PagoDto obtener(Long id) {
        return dto(repository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el pago " + id)));
    }

    /** desde/hasta son dias del negocio (zona de configuracion), ambos inclusive. */
    @Transactional(readOnly = true)
    public Pagina<PagoResumenDto> listar(Long clienteId, Long rutaId, MetodoPago metodo, OrigenPago origen,
                                         Long registradoPorId, LocalDate desde, LocalDate hasta,
                                         int pagina, int tamano) {

        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new ReglaNegocioException("'desde' no puede ser posterior a 'hasta'");
        }
        Configuracion cfg = configuracion.obtenerEntidad();

        // "Pagos del 21 de octubre" = del 21 a las 00:00 al 22 a las 00:00 hora de Torreon
        OffsetDateTime inicio = desde == null ? null
                : desde.atStartOfDay(cfg.zona()).toOffsetDateTime().withOffsetSameInstant(ZoneOffset.UTC);
        OffsetDateTime fin = hasta == null ? null
                : hasta.plusDays(1).atStartOfDay(cfg.zona()).toOffsetDateTime().withOffsetSameInstant(ZoneOffset.UTC);

        int p = Math.max(pagina, 0);
        int t = Math.min(Math.max(tamano, 1), TAMANO_MAXIMO);
        return consultas.buscar(clienteId, rutaId, metodo, origen, registradoPorId, inicio, fin, p, t);
    }

    // ------------------------------------------------------------------
    // Apoyos
    // ------------------------------------------------------------------

    private PagoDto dto(Pago p) {
        Usuario u = p.getRegistradoPor();
        List<PagoDto.Aplicacion> aplicaciones = p.getAplicaciones().stream()
                .map(a -> new PagoDto.Aplicacion(
                        a.getNota().getId(),
                        a.getNota().getFolioOrigen(),
                        a.getMonto(),
                        a.getNota().getSaldo(),
                        a.getNota().getEstado()))
                .toList();

        return new PagoDto(
                p.getId(),
                p.getCliente().getId(),
                p.getCliente().getNombreNegocio(),
                p.getMonto(),
                p.getFechaPago(),
                p.getMetodo(),
                p.getOrigen(),
                u == null ? null : u.getId(),
                u == null ? null : u.getNombre(),
                p.getReferencia(),
                p.getComentario(),
                p.getUuidDispositivo(),
                p.getCreadoEn(),
                aplicaciones,
                p.getMonto().subtract(SaldoAFavorService.remanente(p)),
                SaldoAFavorService.remanente(p),
                consultas.saldoDe(p.getCliente().getId()),
                saldoAFavor.de(p.getCliente().getId()));
    }

    private LocalDate hoy() {
        return configuracion.obtenerEntidad().hoy();
    }

    private static String vacioANull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
