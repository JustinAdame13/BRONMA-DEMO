package mx.bronma.cobranza.nota;

import mx.bronma.cobranza.cliente.Cliente;
import mx.bronma.cobranza.cliente.ClienteRepository;
import mx.bronma.cobranza.common.ConflictoException;
import mx.bronma.cobranza.common.Pagina;
import mx.bronma.cobranza.common.RecursoNoEncontradoException;
import mx.bronma.cobranza.common.ReglaNegocioException;
import mx.bronma.cobranza.configuracion.Configuracion;
import mx.bronma.cobranza.configuracion.ConfiguracionService;
import mx.bronma.cobranza.pago.AplicacionPago;
import mx.bronma.cobranza.pago.AplicacionPagoRepository;
import mx.bronma.cobranza.pago.SaldoAFavorService;
import mx.bronma.cobranza.recordatorio.RecordatorioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Service
public class NotaService {

    private static final int TAMANO_MAXIMO = 100;

    private final NotaRepository repository;
    private final NotaConsultas consultas;
    private final ClienteRepository clientes;
    private final AplicacionPagoRepository aplicaciones;
    private final RecordatorioRepository recordatorios;
    private final ConfiguracionService configuracion;
    private final SaldoAFavorService saldoAFavor;

    public NotaService(NotaRepository repository,
                       NotaConsultas consultas,
                       ClienteRepository clientes,
                       AplicacionPagoRepository aplicaciones,
                       RecordatorioRepository recordatorios,
                       ConfiguracionService configuracion,
                       SaldoAFavorService saldoAFavor) {
        this.repository = repository;
        this.consultas = consultas;
        this.clientes = clientes;
        this.aplicaciones = aplicaciones;
        this.recordatorios = recordatorios;
        this.configuracion = configuracion;
        this.saldoAFavor = saldoAFavor;
    }

    // ------------------------------------------------------------------
    // Lectura
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Pagina<NotaDto> listar(Long clienteId, Long rutaId, EstadoNota estado, Boolean vivas,
                                  Boolean vencidas, String q, LocalDate desde, LocalDate hasta,
                                  int pagina, int tamano) {

        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new ReglaNegocioException("'desde' no puede ser posterior a 'hasta'");
        }
        int p = Math.max(pagina, 0);
        int t = Math.min(Math.max(tamano, 1), TAMANO_MAXIMO);

        return consultas.buscar(clienteId, rutaId, estado, vivas, vencidas, q, desde, hasta, hoy(), p, t);
    }

    @Transactional(readOnly = true)
    public NotaDetalleDto obtener(Long id) {
        NotaDto nota = consultas.porId(id, hoy());
        if (nota == null) {
            throw new RecursoNoEncontradoException("No existe la nota " + id);
        }
        return new NotaDetalleDto(nota, consultas.abonosDe(id));
    }

    /** Para el detalle de cliente: sus notas con saldo. */
    @Transactional(readOnly = true)
    public List<NotaDto> vivasDe(Long clienteId) {
        return consultas.vivasDe(clienteId, hoy());
    }

    // ------------------------------------------------------------------
    // Escritura
    // ------------------------------------------------------------------

    /** Captura manual. Lo normal es que las notas lleguen por la importacion. */
    @Transactional
    public NotaDetalleDto crear(NotaRequest req) {

        String folio = req.folioOrigen().trim();
        if (repository.findByFolioOrigen(folio).isPresent()) {
            throw new ConflictoException("Ya existe una nota con el folio " + folio);
        }

        Cliente cliente = clientes.findById(req.clienteId())
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el cliente " + req.clienteId()));

        LocalDate vencimiento = req.fechaVencimiento() != null
                ? req.fechaVencimiento()
                : req.fechaEmision().plusDays(plazoDe(cliente));
        if (vencimiento.isBefore(req.fechaEmision())) {
            throw new ReglaNegocioException("El vencimiento no puede ser anterior a la fecha de la nota");
        }

        Nota n = new Nota();
        n.setFolioOrigen(folio);
        n.setCliente(cliente);
        n.setRuta(cliente.getRuta()); // foto de la ruta al momento de la venta
        n.setFechaEmision(req.fechaEmision());
        n.setFechaVencimiento(vencimiento);
        n.setMonto(req.monto());
        n.setSaldo(req.monto());
        n.setEstado(EstadoNota.ABIERTA);
        repository.save(n);

        // Si el cliente tenia saldo a favor, se usa de inmediato en esta nota
        saldoAFavor.aplicar(cliente.getId());

        return obtener(n.getId());
    }

    /**
     * Anula una nota (venta devuelta o capturada por error). No se borra: deja
     * de contar en la cartera y sus recordatorios pendientes se cancelan.
     * Si ya tenia abonos, ese dinero se le regresa al cliente como saldo a
     * favor (y se usa de inmediato en sus otras notas con saldo, si tiene).
     */
    @Transactional
    public NotaDetalleDto cancelar(Long id) {
        Nota n = repository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la nota " + id));

        if (n.getEstado() == EstadoNota.CANCELADA) {
            return obtener(id); // idempotente: cancelar dos veces no es error
        }
        // Los abonos se quitan de la nota: el pago queda con ese remanente = saldo a favor
        List<AplicacionPago> abonos = aplicaciones.findByNotaId(id);
        for (AplicacionPago a : abonos) {
            a.getPago().getAplicaciones().remove(a); // orphanRemoval la borra
        }

        n.setSaldo(n.getMonto());
        n.setEstado(EstadoNota.CANCELADA);
        repository.saveAndFlush(n);
        recordatorios.cancelarPendientes(Set.of(id));

        if (!abonos.isEmpty()) {
            saldoAFavor.aplicar(n.getCliente().getId());
        }

        return obtener(id);
    }

    // ------------------------------------------------------------------
    // Apoyos
    // ------------------------------------------------------------------

    private LocalDate hoy() {
        return configuracion.obtenerEntidad().hoy();
    }

    private int plazoDe(Cliente c) {
        if (c.getPlazoDias() != null) {
            return c.getPlazoDias();
        }
        Configuracion cfg = configuracion.obtenerEntidad();
        return cfg.getPlazoCreditoDefaultDias();
    }
}
