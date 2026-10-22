package mx.bronma.cobranza.cliente;

import mx.bronma.cobranza.common.ConflictoException;
import mx.bronma.cobranza.common.Dinero;
import mx.bronma.cobranza.common.Pagina;
import mx.bronma.cobranza.common.RecursoNoEncontradoException;
import mx.bronma.cobranza.common.ReglaNegocioException;
import mx.bronma.cobranza.configuracion.Configuracion;
import mx.bronma.cobranza.configuracion.ConfiguracionService;
import mx.bronma.cobranza.ruta.Ruta;
import mx.bronma.cobranza.ruta.RutaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Optional;

@Service
public class ClienteService {

    /** Arriba de esto la coordenada no sirve para ubicar una tienda en una calle. */
    private static final int PRECISION_MAXIMA_METROS = 100;

    /** Tope del contrato para ?tamano=. */
    private static final int TAMANO_MAXIMO = 100;

    private final ClienteRepository repository;
    private final ClienteConsultas consultas;
    private final RutaRepository rutaRepository;
    private final ConfiguracionService configuracion;

    public ClienteService(ClienteRepository repository,
                          ClienteConsultas consultas,
                          RutaRepository rutaRepository,
                          ConfiguracionService configuracion) {
        this.repository = repository;
        this.consultas = consultas;
        this.rutaRepository = rutaRepository;
        this.configuracion = configuracion;
    }

    // ------------------------------------------------------------------
    // Lectura
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Pagina<ClienteResumenDto> listar(Long rutaId, EstadoCredito estadoCredito,
                                            String q, Boolean sinUbicacion, Boolean activo,
                                            int pagina, int tamano) {

        int p = Math.max(pagina, 0);
        int t = Math.min(Math.max(tamano, 1), TAMANO_MAXIMO);

        return consultas.buscar(rutaId, estadoCredito, q, sinUbicacion, activo, p, t);
    }

    @Transactional(readOnly = true)
    public ClienteDetalleDto obtener(Long id) {
        return detalle(buscar(id));
    }

    // ------------------------------------------------------------------
    // Escritura
    // ------------------------------------------------------------------

    @Transactional
    public ClienteDetalleDto crear(ClienteRequest req) {

        if (repository.findByCodigoOrigen(req.codigoOrigen()).isPresent()) {
            throw new ConflictoException("Ya existe un cliente con el codigo " + req.codigoOrigen());
        }

        Cliente c = new Cliente();
        aplicar(c, req);

        return detalle(repository.save(c));
    }

    @Transactional
    public ClienteDetalleDto actualizar(Long id, ClienteRequest req) {

        Cliente c = buscar(id);

        // Un cliente no choca consigo mismo: solo es duplicado si el codigo lo tiene OTRO.
        Optional<Cliente> conMismoCodigo = repository.findByCodigoOrigen(req.codigoOrigen());
        if (conMismoCodigo.isPresent() && !conMismoCodigo.get().getId().equals(id)) {
            throw new ConflictoException("Ya existe un cliente con el codigo " + req.codigoOrigen());
        }

        aplicar(c, req);

        return detalle(repository.save(c));
    }

    /**
     * Fija o borra la ubicacion. Las tres (latitud, longitud, fuente) van
     * juntas o las tres en null.
     */
    @Transactional
    public UbicacionDto fijarUbicacion(Long id, UbicacionRequest req) {

        Cliente c = buscar(id);

        boolean borrar = req.latitud() == null && req.longitud() == null && req.fuente() == null;
        boolean completa = req.latitud() != null && req.longitud() != null && req.fuente() != null;

        // Media ubicacion no ubica nada, y la base tiene un CHECK que la rechaza:
        // se corta aqui para devolver un 422 con explicacion y no un 500 de Postgres.
        if (!borrar && !completa) {
            throw new ReglaNegocioException(
                    "Manda latitud, longitud y fuente juntas, o las tres en null para borrar la ubicacion");
        }

        if (borrar) {
            c.setLatitud(null);
            c.setLongitud(null);
            c.setUbicacionFuente(null);
            c.setUbicacionFijadaEn(null);
            return UbicacionDto.from(repository.save(c));
        }

        if (req.fuente() == UbicacionFuente.GEOCODIFICADA) {
            throw new ReglaNegocioException(
                    "GEOCODIFICADA esta reservada para el backend: manda MANUAL o GPS");
        }

        if (req.precisionMetros() != null && req.precisionMetros() > PRECISION_MAXIMA_METROS) {
            throw new ReglaNegocioException("Precision insuficiente, intenta de nuevo al aire libre");
        }

        c.setLatitud(req.latitud());
        c.setLongitud(req.longitud());
        c.setUbicacionFuente(req.fuente());
        c.setUbicacionFijadaEn(Instant.now());

        return UbicacionDto.from(repository.save(c));
    }

    // ------------------------------------------------------------------
    // Apoyos
    // ------------------------------------------------------------------

    private Cliente buscar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el cliente " + id));
    }

    /** Copia el request a la entidad. La ubicacion no se toca: tiene su propio endpoint. */
    private void aplicar(Cliente c, ClienteRequest req) {

        Ruta ruta = null;
        if (req.rutaId() != null) {
            ruta = rutaRepository.findById(req.rutaId())
                    .orElseThrow(() -> new RecursoNoEncontradoException("No existe la ruta " + req.rutaId()));
        }

        c.setCodigoOrigen(req.codigoOrigen());
        c.setNombreNegocio(req.nombreNegocio());
        c.setContacto(req.contacto());
        c.setTelefonoWhatsapp(req.telefonoWhatsapp());
        c.setDireccion(req.direccion());
        c.setRuta(ruta);
        c.setLimiteCredito(req.limiteCredito() == null
                ? BigDecimal.ZERO
                : Dinero.redondear(req.limiteCredito()));
        c.setPlazoDias(req.plazoDias());
        c.setEstadoCredito(req.estadoCredito());
        c.setAceptaWhatsapp(req.aceptaWhatsapp());

        // null no cambia nada: en el alta se queda el true por defecto de la entidad.
        if (req.activo() != null) {
            c.setActivo(req.activo());
        }

        // Un cliente dado de baja ya no se visita: sale del orden de su ruta.
        // (reordenar tampoco lo toma en cuenta, porque solo lee los activos.)
        if (!c.getActivo()) {
            c.setOrdenVisita(null);
        }
    }

    /** Arma el detalle resolviendo los defaults de configuracion y los saldos. */
    private ClienteDetalleDto detalle(Cliente c) {

        SaldoCliente saldos = consultas.saldosDe(c.getId());
        Configuracion cfg = configuracion.obtenerEntidad();

        // limite 0 y plazo null significan "usar el default": el front muestra
        // los ...Efectivo, no los capturados.
        BigDecimal limite = Dinero.nz(c.getLimiteCredito());
        BigDecimal limiteEfectivo = limite.signum() > 0 ? limite : Dinero.nz(cfg.getLimiteCreditoDefault());

        Integer plazoEfectivo = c.getPlazoDias() != null
                ? c.getPlazoDias()
                : cfg.getPlazoCreditoDefaultDias();

        // Sin limite con que comparar, el porcentaje no significa nada.
        BigDecimal usoCredito = limiteEfectivo.signum() > 0
                ? saldos.saldo().divide(limiteEfectivo, 2, RoundingMode.HALF_UP)
                : null;

        Ruta r = c.getRuta();

        return new ClienteDetalleDto(
                c.getId(),
                c.getCodigoOrigen(),
                c.getNombreNegocio(),
                r == null ? null : r.getId(),
                r == null ? null : r.getNombre(),
                c.getContacto(),
                c.getTelefonoWhatsapp(),
                c.getDireccion(),
                c.getEstadoCredito(),
                saldos.saldo(),
                saldos.saldoVencido(),
                limite,
                limiteEfectivo,
                c.getPlazoDias(),
                plazoEfectivo,
                usoCredito,
                c.getAceptaWhatsapp(),
                c.getLatitud(),
                c.getLongitud(),
                c.getUbicacionFuente(),
                c.getUbicacionFijadaEn(),
                c.getOrdenVisita(),
                c.getActivo());
    }
}
