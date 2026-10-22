package mx.bronma.cobranza.importacion;

import mx.bronma.cobranza.cliente.Cliente;
import mx.bronma.cobranza.cliente.ClienteRepository;
import mx.bronma.cobranza.common.Pagina;
import mx.bronma.cobranza.common.RecursoNoEncontradoException;
import mx.bronma.cobranza.configuracion.ConfiguracionService;
import mx.bronma.cobranza.importacion.Interprete.TipoVenta;
import mx.bronma.cobranza.importacion.Interprete.ValorInvalidoException;
import mx.bronma.cobranza.nota.EstadoNota;
import mx.bronma.cobranza.nota.Nota;
import mx.bronma.cobranza.nota.NotaRepository;
import mx.bronma.cobranza.pago.AplicacionPagoRepository;
import mx.bronma.cobranza.pago.SaldoAFavorService;
import mx.bronma.cobranza.ruta.Ruta;
import mx.bronma.cobranza.ruta.RutaRepository;
import mx.bronma.cobranza.usuario.Usuario;
import mx.bronma.cobranza.usuario.UsuarioRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static mx.bronma.cobranza.importacion.ColumnaImportacion.*;

/**
 * Importacion de ventas a credito desde el Excel/CSV del sistema de ventas.
 *
 * Idempotente: el folio de la nota y la clave del cliente son las llaves.
 * Subir el mismo archivo dos veces no duplica nada; la segunda vez todo cae
 * en "sinCambios".
 *
 * Reglas:
 * <ul>
 *   <li>Una fila mala no tumba el archivo: se anota en detalleErrores y se
 *       importan las demas.</li>
 *   <li>Si el sistema exporta una fila por producto, las filas del mismo folio
 *       se suman en una sola nota (deben tener el mismo cliente y fecha).</li>
 *   <li>Las ventas de CONTADO se omiten: no son cuentas por cobrar.</li>
 *   <li>Una nota que ya recibio abonos no cambia de monto ni de cliente desde
 *       la importacion: eso descuadraria lo cobrado. Sus fechas si se
 *       actualizan.</li>
 *   <li>Las notas CANCELADAS en Bronma no se tocan.</li>
 *   <li>Clientes y rutas que no existen se dan de alta. Al cliente nuevo no se
 *       le activa WhatsApp: el consentimiento lo captura la oficina.</li>
 *   <li>Un telefono que no se puede llevar a E.164 se ignora (no es error).</li>
 *   <li>Si el cliente tenia saldo a favor, se aplica a sus notas nuevas.</li>
 * </ul>
 *
 * Todo va en una transaccion. Con simular=true se hace el proceso completo y
 * al final se deshace: la oficina ve que pasaria sin guardar nada.
 */
@Service
public class ImportacionService {

    /** Para que una importacion con miles de errores no guarde un JSON enorme. */
    static final int MAXIMO_ERRORES_DETALLE = 500;

    /** El mismo gris que pone V3 por defecto. La oficina le pone color despues. */
    private static final String COLOR_RUTA_NUEVA = "#64748B";

    private static final int TAMANO_MAXIMO = 100;

    private static final TypeReference<List<ErrorFila>> LISTA_ERRORES = new TypeReference<>() {
    };

    private final LectorArchivo lector;
    private final ImportacionRepository importaciones;
    private final ClienteRepository clientes;
    private final RutaRepository rutas;
    private final NotaRepository notas;
    private final AplicacionPagoRepository aplicaciones;
    private final UsuarioRepository usuarios;
    private final ConfiguracionService configuracion;
    private final JsonMapper json;
    private final SaldoAFavorService saldoAFavor;

    public ImportacionService(LectorArchivo lector,
                              ImportacionRepository importaciones,
                              ClienteRepository clientes,
                              RutaRepository rutas,
                              NotaRepository notas,
                              AplicacionPagoRepository aplicaciones,
                              UsuarioRepository usuarios,
                              ConfiguracionService configuracion,
                              JsonMapper json,
                              SaldoAFavorService saldoAFavor) {
        this.lector = lector;
        this.importaciones = importaciones;
        this.clientes = clientes;
        this.rutas = rutas;
        this.notas = notas;
        this.aplicaciones = aplicaciones;
        this.usuarios = usuarios;
        this.configuracion = configuracion;
        this.json = json;
        this.saldoAFavor = saldoAFavor;
    }

    // ------------------------------------------------------------------
    // Importar
    // ------------------------------------------------------------------

    @Transactional
    public ImportacionDto importar(MultipartFile archivo, Long usuarioId, boolean simular) {

        Usuario usuario = usuarioId == null ? null : usuarios.findById(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el usuario " + usuarioId));

        // 1. Leer el archivo. Si la estructura esta mal (sin columnas, vacio...) aqui sale un 422.
        List<FilaArchivo> filas = lector.leer(archivo);

        Corrida corrida = new Corrida();
        corrida.filasTotal = filas.size();

        // 2. Interpretar cada fila y juntar las del mismo folio
        Map<String, NotaArchivo> porFolio = agrupar(filas, corrida);

        // 3. Traer de la base, de un jalon, todo lo que el archivo menciona
        Contexto ctx = cargarContexto(porFolio.values());

        // 4. Bitacora. Se guarda antes que las notas porque estas apuntan a ella.
        Importacion bitacora = new Importacion();
        bitacora.setArchivoNombre(Interprete.recortar(
                Objects.requireNonNullElse(archivo.getOriginalFilename(), "archivo"), 255));
        bitacora.setUsuario(usuario);
        importaciones.save(bitacora);

        // 5. Aplicar nota por nota
        for (NotaArchivo n : porFolio.values()) {
            try {
                aplicar(n, ctx, bitacora, corrida);
            } catch (ValorInvalidoException e) {
                corrida.error(n.fila, n.folio, e.getMessage());
            }
        }

        // 6. Clientes con saldo a favor (anticipos o pagos de mas): se usa en sus notas nuevas
        List<Long> idsClientes = ctx.clientes.values().stream().map(Cliente::getId).toList();
        for (Long clienteId : saldoAFavor.conSaldoAFavor(idsClientes)) {
            saldoAFavor.aplicar(clienteId);
        }

        // 7. Cerrar la bitacora
        bitacora.setFilasTotal(corrida.filasTotal);
        bitacora.setCreadas(corrida.creadas);
        bitacora.setActualizadas(corrida.actualizadas);
        bitacora.setSinCambios(corrida.sinCambios);
        bitacora.setOmitidas(corrida.omitidas);
        bitacora.setErrores(corrida.errores.size());
        bitacora.setClientesNuevos(corrida.clientesNuevos);
        bitacora.setRutasNuevas(corrida.rutasNuevas);
        bitacora.setDetalleErrores(corrida.errores.isEmpty()
                ? null
                : json.writeValueAsString(corrida.erroresParaGuardar()));
        importaciones.save(bitacora);

        if (simular) {
            // Se hizo todo para poder contarlo, pero nada se queda en la base
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return dto(bitacora, corrida.erroresParaGuardar(), true);
        }
        return dto(bitacora, corrida.erroresParaGuardar(), false);
    }

    /** Interpreta las filas y junta las del mismo folio. Los errores quedan en la corrida. */
    private Map<String, NotaArchivo> agrupar(List<FilaArchivo> filas, Corrida corrida) {

        Map<String, NotaArchivo> porFolio = new LinkedHashMap<>();
        Set<String> foliosConError = new HashSet<>();

        for (FilaArchivo f : filas) {
            String folio = f.valor(FOLIO);
            String codigo = f.valor(CLIENTE_CODIGO);

            // Sin folio ni cliente: renglon de totales o notas al pie del reporte
            if (folio == null && codigo == null) {
                corrida.filasTotal--;
                continue;
            }
            // Otra fila de un folio que ya fallo: ya se reporto una vez
            if (folio != null && foliosConError.contains(folio)) {
                continue;
            }

            try {
                if (Interprete.tipoVenta(f.valor(TIPO_VENTA)) == TipoVenta.CONTADO) {
                    corrida.omitidas++;
                    continue;
                }

                VentaFila v = interpretar(f);
                NotaArchivo existente = porFolio.get(v.folio());
                if (existente == null) {
                    porFolio.put(v.folio(), new NotaArchivo(v));
                } else {
                    existente.sumar(v);
                }

            } catch (ValorInvalidoException e) {
                corrida.error(f.numero(), folio, e.getMessage());
                if (folio != null) {
                    foliosConError.add(folio);
                    porFolio.remove(folio); // lo que ya se habia juntado de ese folio no sirve
                }
            }
        }
        return porFolio;
    }

    private VentaFila interpretar(FilaArchivo f) {

        String folio = obligatorio(f, FOLIO, "La fila no trae folio");
        if (folio.length() > 40) {
            throw new ValorInvalidoException("El folio pasa de 40 caracteres");
        }

        String codigo = obligatorio(f, CLIENTE_CODIGO, "La fila no trae la clave del cliente");
        if (codigo.length() > 30) {
            throw new ValorInvalidoException("La clave del cliente '" + codigo + "' pasa de 30 caracteres");
        }

        String ruta = f.valor(RUTA);
        if (ruta != null && ruta.length() > 30) {
            throw new ValorInvalidoException("La clave de ruta '" + ruta + "' pasa de 30 caracteres");
        }

        LocalDate emision = Interprete.fecha(
                obligatorio(f, FECHA_EMISION, "La fila no trae fecha"), "La fecha");
        BigDecimal monto = Interprete.monto(
                obligatorio(f, MONTO, "La fila no trae monto"));
        LocalDate vencimiento = f.tiene(FECHA_VENCIMIENTO)
                ? Interprete.fecha(f.valor(FECHA_VENCIMIENTO), "El vencimiento")
                : null;
        Integer plazo = f.tiene(PLAZO_DIAS) ? Interprete.plazoDias(f.valor(PLAZO_DIAS)) : null;

        return new VentaFila(
                f.numero(),
                folio,
                codigo,
                Interprete.recortar(f.valor(CLIENTE_NOMBRE), 150),
                ruta,
                Interprete.telefonoE164(f.valor(TELEFONO)),
                Interprete.recortar(f.valor(DIRECCION), 255),
                emision,
                vencimiento,
                plazo,
                monto);
    }

    private static String obligatorio(FilaArchivo f, ColumnaImportacion columna, String mensaje) {
        String v = f.valor(columna);
        if (v == null) {
            throw new ValorInvalidoException(mensaje);
        }
        return v;
    }

    private Contexto cargarContexto(Collection<NotaArchivo> notasArchivo) {

        Set<String> codigosCliente = new HashSet<>();
        Set<String> codigosRuta = new HashSet<>();
        Set<String> folios = new HashSet<>();
        for (NotaArchivo n : notasArchivo) {
            codigosCliente.add(n.clienteCodigo);
            folios.add(n.folio);
            if (n.rutaCodigo != null) {
                codigosRuta.add(n.rutaCodigo);
            }
        }

        // Los "IN" vacios se evitan: no hace falta ir a la base
        Map<String, Cliente> clientesPorCodigo = codigosCliente.isEmpty() ? new HashMap<>()
                : porLlave(clientes.findByCodigoOrigenIn(codigosCliente), Cliente::getCodigoOrigen);
        Map<String, Ruta> rutasPorCodigo = codigosRuta.isEmpty() ? new HashMap<>()
                : porLlave(rutas.findByCodigoOrigenIn(codigosRuta), Ruta::getCodigoOrigen);
        Map<String, Nota> notasPorFolio = folios.isEmpty() ? new HashMap<>()
                : porLlave(notas.findByFolioOrigenIn(folios), Nota::getFolioOrigen);

        Set<Long> idsNotas = notasPorFolio.values().stream().map(Nota::getId).collect(Collectors.toSet());
        Set<Long> notasConPagos = idsNotas.isEmpty() ? Set.of() : aplicaciones.notasConPagos(idsNotas);

        return new Contexto(clientesPorCodigo, rutasPorCodigo, notasPorFolio, notasConPagos,
                configuracion.obtenerEntidad().getPlazoCreditoDefaultDias());
    }

    private static <T> Map<String, T> porLlave(List<T> lista, Function<T, String> llave) {
        Map<String, T> mapa = new HashMap<>();
        for (T t : lista) {
            mapa.put(llave.apply(t), t);
        }
        return mapa;
    }

    /**
     * Crea o actualiza una nota. Primero valida todo (sin escribir) y despues
     * escribe: asi una nota con error no deja clientes o rutas creados a medias.
     */
    private void aplicar(NotaArchivo n, Contexto ctx, Importacion bitacora, Corrida corrida) {

        Cliente clienteExistente = ctx.clientes.get(n.clienteCodigo);
        Nota notaExistente = ctx.notas.get(n.folio);

        // ---------- Validar ----------

        if (clienteExistente == null && n.clienteNombre == null) {
            throw new ValorInvalidoException("El cliente " + n.clienteCodigo
                    + " es nuevo y la fila no trae su nombre para darlo de alta");
        }

        LocalDate vencimiento = calcularVencimiento(n, clienteExistente, ctx.plazoDefault);
        if (vencimiento.isBefore(n.emision)) {
            throw new ValorInvalidoException("El vencimiento (" + vencimiento
                    + ") es anterior a la fecha de la nota (" + n.emision + ")");
        }

        if (notaExistente != null && notaExistente.getEstado() == EstadoNota.CANCELADA) {
            // Cancelada en Bronma: la importacion no la revive ni la toca
            corrida.sinCambios++;
            return;
        }

        boolean clienteCambia = notaExistente != null
                && (clienteExistente == null || !notaExistente.getCliente().getId().equals(clienteExistente.getId()));
        boolean montoCambia = notaExistente != null && notaExistente.getMonto().compareTo(n.monto) != 0;

        if (notaExistente != null && (clienteCambia || montoCambia) && tieneAbonos(notaExistente, ctx)) {
            throw new ValorInvalidoException(clienteCambia
                    ? "La nota ya tiene abonos; no se puede cambiar de cliente desde la importacion"
                    : "La nota ya tiene abonos; no se puede cambiar su monto desde la importacion "
                    + "(en Bronma $" + notaExistente.getMonto() + ", en el archivo $" + n.monto + ")");
        }

        // ---------- Escribir ----------

        Ruta ruta = resolverRuta(n, ctx, corrida);
        Cliente cliente = clienteExistente != null
                ? actualizarCliente(clienteExistente, n, ruta)
                : crearCliente(n, ruta, ctx, corrida);

        // La ruta de la nota es una foto del momento de la venta: la del archivo o la del cliente
        Ruta rutaNota = ruta != null ? ruta : cliente.getRuta();

        if (notaExistente == null) {
            Nota nota = new Nota();
            nota.setFolioOrigen(n.folio);
            nota.setCliente(cliente);
            nota.setRuta(rutaNota);
            nota.setImportacion(bitacora);
            nota.setFechaEmision(n.emision);
            nota.setFechaVencimiento(vencimiento);
            nota.setMonto(n.monto);
            nota.setSaldo(n.monto);
            nota.setEstado(EstadoNota.ABIERTA);
            notas.save(nota);
            ctx.notas.put(n.folio, nota);
            corrida.creadas++;
            return;
        }

        boolean cambio = false;
        Nota e = notaExistente;

        if (clienteCambia) {
            e.setCliente(cliente);
            e.setRuta(rutaNota);
            cambio = true;
        } else if (ruta != null && (e.getRuta() == null || !e.getRuta().getId().equals(ruta.getId()))) {
            e.setRuta(ruta);
            cambio = true;
        }
        if (!e.getFechaEmision().equals(n.emision)) {
            e.setFechaEmision(n.emision);
            cambio = true;
        }
        if (!e.getFechaVencimiento().equals(vencimiento)) {
            e.setFechaVencimiento(vencimiento);
            cambio = true;
        }
        if (montoCambia) {
            // Solo llega aqui sin abonos: saldo = monto y sigue ABIERTA
            e.setMonto(n.monto);
            e.setSaldo(n.monto);
            cambio = true;
        }

        if (cambio) {
            corrida.actualizadas++;
        } else {
            corrida.sinCambios++;
        }
    }

    /** Vencimiento del archivo; si no viene, fecha + plazo (de la fila, del cliente o el general). */
    private static LocalDate calcularVencimiento(NotaArchivo n, Cliente cliente, int plazoDefault) {
        if (n.vencimiento != null) {
            return n.vencimiento;
        }
        int plazo;
        if (n.plazo != null) {
            plazo = n.plazo;
        } else if (cliente != null && cliente.getPlazoDias() != null) {
            plazo = cliente.getPlazoDias();
        } else {
            plazo = plazoDefault;
        }
        return n.emision.plusDays(plazo);
    }

    /** Con aplicaciones de pago, o con un estado que ya no es ABIERTA, la nota ya recibio dinero. */
    private static boolean tieneAbonos(Nota nota, Contexto ctx) {
        return ctx.notasConPagos.contains(nota.getId()) || nota.getEstado() != EstadoNota.ABIERTA;
    }

    private Ruta resolverRuta(NotaArchivo n, Contexto ctx, Corrida corrida) {
        if (n.rutaCodigo == null) {
            return null;
        }
        Ruta ruta = ctx.rutas.get(n.rutaCodigo);
        if (ruta == null) {
            ruta = new Ruta();
            ruta.setCodigoOrigen(n.rutaCodigo);
            ruta.setNombre(Interprete.recortar("Ruta " + n.rutaCodigo, 100));
            ruta.setColor(COLOR_RUTA_NUEVA);
            ruta.setActivo(true);
            rutas.save(ruta);
            ctx.rutas.put(n.rutaCodigo, ruta);
            corrida.rutasNuevas++;
        }
        return ruta;
    }

    private Cliente crearCliente(NotaArchivo n, Ruta ruta, Contexto ctx, Corrida corrida) {
        Cliente c = new Cliente();
        c.setCodigoOrigen(n.clienteCodigo);
        c.setNombreNegocio(n.clienteNombre);
        c.setTelefonoWhatsapp(n.telefono);
        c.setDireccion(n.direccion);
        c.setRuta(ruta);
        // limite 0 y plazo null = los de configuracion; estado ACTIVO; aceptaWhatsapp false
        clientes.save(c);
        ctx.clientes.put(n.clienteCodigo, c);
        corrida.clientesNuevos++;
        return c;
    }

    /**
     * El sistema de ventas manda en nombre, ruta, telefono y direccion, pero
     * solo si el archivo los trae: una columna vacia no borra lo capturado.
     * Credito, ubicacion, consentimiento y baja son de Bronma y no se tocan.
     */
    private static Cliente actualizarCliente(Cliente c, NotaArchivo n, Ruta ruta) {
        if (c == null) {
            return null;
        }
        if (n.clienteNombre != null && !n.clienteNombre.equals(c.getNombreNegocio())) {
            c.setNombreNegocio(n.clienteNombre);
        }
        if (n.telefono != null && !n.telefono.equals(c.getTelefonoWhatsapp())) {
            c.setTelefonoWhatsapp(n.telefono);
        }
        if (n.direccion != null && !n.direccion.equals(c.getDireccion())) {
            c.setDireccion(n.direccion);
        }
        if (ruta != null && (c.getRuta() == null || !c.getRuta().getId().equals(ruta.getId()))) {
            c.setRuta(ruta);
            // Su lugar en el orden de visita era de la otra ruta
            c.setOrdenVisita(null);
        }
        return c;
    }

    // ------------------------------------------------------------------
    // Consultas
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Pagina<ImportacionResumenDto> listar(int pagina, int tamano) {
        int p = Math.max(pagina, 0);
        int t = Math.min(Math.max(tamano, 1), TAMANO_MAXIMO);

        Page<Importacion> page = importaciones.findAllByOrderByImportadoEnDescIdDesc(PageRequest.of(p, t));
        List<ImportacionResumenDto> contenido = page.getContent().stream()
                .map(ImportacionResumenDto::from)
                .toList();

        return Pagina.of(contenido, p, t, page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public ImportacionDto obtener(Long id) {
        Importacion i = importaciones.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la importacion " + id));

        List<ErrorFila> errores = i.getDetalleErrores() == null
                ? List.of()
                : json.readValue(i.getDetalleErrores(), LISTA_ERRORES);

        return dto(i, errores, false);
    }

    private static ImportacionDto dto(Importacion i, List<ErrorFila> errores, boolean simulada) {
        return new ImportacionDto(
                simulada ? null : i.getId(),
                i.getArchivoNombre(),
                simulada ? null : i.getImportadoEn(),
                i.getUsuario() == null ? null : i.getUsuario().getId(),
                simulada,
                i.getFilasTotal(),
                i.getCreadas(),
                i.getActualizadas(),
                i.getSinCambios(),
                i.getOmitidas(),
                i.getErrores(),
                i.getClientesNuevos(),
                i.getRutasNuevas(),
                errores);
    }

    // ------------------------------------------------------------------
    // Estructuras internas
    // ------------------------------------------------------------------

    /** Una fila ya interpretada. */
    private record VentaFila(
            int fila, String folio, String clienteCodigo, String clienteNombre, String rutaCodigo,
            String telefono, String direccion, LocalDate emision, LocalDate vencimiento,
            Integer plazo, BigDecimal monto) {
    }

    /** Una nota del archivo: una o varias filas con el mismo folio, ya sumadas. */
    private static final class NotaArchivo {

        final int fila;
        final String folio;
        final String clienteCodigo;
        final LocalDate emision;
        String clienteNombre;
        String rutaCodigo;
        String telefono;
        String direccion;
        LocalDate vencimiento;
        Integer plazo;
        BigDecimal monto;

        NotaArchivo(VentaFila v) {
            this.fila = v.fila();
            this.folio = v.folio();
            this.clienteCodigo = v.clienteCodigo();
            this.emision = v.emision();
            this.clienteNombre = v.clienteNombre();
            this.rutaCodigo = v.rutaCodigo();
            this.telefono = v.telefono();
            this.direccion = v.direccion();
            this.vencimiento = v.vencimiento();
            this.plazo = v.plazo();
            this.monto = v.monto();
        }

        /** Otra fila del mismo folio (una fila por producto): se suma el monto. */
        void sumar(VentaFila v) {
            if (!clienteCodigo.equals(v.clienteCodigo())) {
                throw new ValorInvalidoException("El folio " + folio + " aparece con dos clientes distintos ("
                        + clienteCodigo + " en la fila " + fila + " y " + v.clienteCodigo() + ")");
            }
            if (!emision.equals(v.emision())) {
                throw new ValorInvalidoException("El folio " + folio + " aparece con dos fechas distintas ("
                        + emision + " en la fila " + fila + " y " + v.emision() + ")");
            }
            if (vencimiento != null && v.vencimiento() != null && !vencimiento.equals(v.vencimiento())) {
                throw new ValorInvalidoException("El folio " + folio + " aparece con dos vencimientos distintos");
            }
            monto = Interprete.monto(monto.add(v.monto()).toPlainString()); // revisa el tope otra vez
            if (vencimiento == null) vencimiento = v.vencimiento();
            if (plazo == null) plazo = v.plazo();
            if (clienteNombre == null) clienteNombre = v.clienteNombre();
            if (rutaCodigo == null) rutaCodigo = v.rutaCodigo();
            if (telefono == null) telefono = v.telefono();
            if (direccion == null) direccion = v.direccion();
        }
    }

    /** Lo que ya existe en la base. Los mapas crecen con lo que se va creando. */
    private record Contexto(
            Map<String, Cliente> clientes,
            Map<String, Ruta> rutas,
            Map<String, Nota> notas,
            Set<Long> notasConPagos,
            int plazoDefault) {
    }

    /** Contadores y errores de una importacion. */
    private static final class Corrida {
        int filasTotal;
        int creadas;
        int actualizadas;
        int sinCambios;
        int omitidas;
        int clientesNuevos;
        int rutasNuevas;
        final List<ErrorFila> errores = new ArrayList<>();

        void error(int fila, String folio, String mensaje) {
            errores.add(new ErrorFila(fila, folio, mensaje));
        }

        List<ErrorFila> erroresParaGuardar() {
            return errores.size() <= MAXIMO_ERRORES_DETALLE ? errores : errores.subList(0, MAXIMO_ERRORES_DETALLE);
        }
    }
}
