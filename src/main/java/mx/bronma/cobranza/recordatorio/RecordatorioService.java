package mx.bronma.cobranza.recordatorio;

import mx.bronma.cobranza.cliente.Cliente;
import mx.bronma.cobranza.common.ConflictoException;
import mx.bronma.cobranza.common.FiltroSql;
import mx.bronma.cobranza.common.Pagina;
import mx.bronma.cobranza.common.RecursoNoEncontradoException;
import mx.bronma.cobranza.configuracion.Configuracion;
import mx.bronma.cobranza.configuracion.ConfiguracionService;
import mx.bronma.cobranza.nota.Nota;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * B6: recordatorios de pago por WhatsApp.
 *
 * Cada corrida:
 * <ol>
 *   <li>Genera los PENDIENTE cruzando notas vivas con las reglas activas
 *       (INSERT ... ON CONFLICT DO NOTHING: el UNIQUE (nota, regla) evita duplicados).</li>
 *   <li>Cancela lo que ya no aplica: notas pagadas o canceladas, y pendientes
 *       de hace mas de 2 dias (mandar "vence en 3 dias" una semana tarde confunde).</li>
 *   <li>Si whatsapp_habilitado = true, manda los de hoy (y reintenta los
 *       FALLIDO con menos de 3 intentos). Respeta acepta_whatsapp.</li>
 * </ol>
 * Corre sola cada dia a la hora_envio_recordatorios de configuracion, y a mano
 * con POST /recordatorios/ejecutar.
 */
@Service
public class RecordatorioService {

    private static final Logger log = LoggerFactory.getLogger(RecordatorioService.class);

    static final int MAXIMO_INTENTOS = 3;
    /** Un pendiente con mas dias de retraso que esto se cancela en lugar de mandarse. */
    static final int DIAS_GRACIA_ENVIO = 2;
    private static final int MAXIMO_POR_CORRIDA = 500;
    private static final int TAMANO_MAXIMO = 100;

    private final RecordatorioRepository repository;
    private final PoliticaRecordatorioRepository politicas;
    private final NamedParameterJdbcTemplate jdbc;
    private final ConfiguracionService configuracion;
    private final EnviadorMensajes enviador;
    private final TransactionTemplate tx;

    /** Una sola corrida a la vez (la del reloj y la manual podrian cruzarse). */
    private final ReentrantLock corriendo = new ReentrantLock();
    private volatile LocalDate ultimaCorridaAutomatica;

    public RecordatorioService(RecordatorioRepository repository,
                               PoliticaRecordatorioRepository politicas,
                               NamedParameterJdbcTemplate jdbc,
                               ConfiguracionService configuracion,
                               EnviadorMensajes enviador,
                               TransactionTemplate tx) {
        this.repository = repository;
        this.politicas = politicas;
        this.jdbc = jdbc;
        this.configuracion = configuracion;
        this.enviador = enviador;
        this.tx = tx;
    }

    // ------------------------------------------------------------------
    // Programacion
    // ------------------------------------------------------------------

    /**
     * Revisa cada 10 minutos si ya es la hora de envio de hoy. La hora vive en
     * la tabla configuracion (se cambia con la API), por eso no va fija en el cron.
     */
    @Scheduled(cron = "0 */10 * * * *")
    public void revisarHora() {
        try {
            Configuracion cfg = configuracion.obtenerEntidad();
            ZonedDateTime ahora = ZonedDateTime.now(cfg.zona());
            LocalDate hoy = ahora.toLocalDate();
            if (hoy.equals(ultimaCorridaAutomatica) || ahora.toLocalTime().isBefore(cfg.getHoraEnvioRecordatorios())) {
                return;
            }
            ultimaCorridaAutomatica = hoy;
            ResumenEjecucion r = ejecutar();
            log.info("Recordatorios del dia: {}", r);
        } catch (ConflictoException ocupado) {
            // Alguien la corrio a mano en ese momento: con una basta
        } catch (Exception e) {
            log.error("Fallo la corrida diaria de recordatorios", e);
        }
    }

    // ------------------------------------------------------------------
    // Corrida
    // ------------------------------------------------------------------

    public ResumenEjecucion ejecutar() {
        if (!corriendo.tryLock()) {
            throw new ConflictoException("Ya hay una corrida de recordatorios en curso");
        }
        try {
            Configuracion cfg = configuracion.obtenerEntidad();
            LocalDate hoy = cfg.hoy();

            Integer generados = tx.execute(st -> generar(hoy));
            Integer cancelados = tx.execute(st -> cancelarLoQueYaNoAplica(hoy));

            if (!Boolean.TRUE.equals(cfg.getWhatsappHabilitado())) {
                return new ResumenEjecucion(hoy, enviador.nombre(), n(generados), 0, 0, n(cancelados),
                        "WhatsApp deshabilitado en configuracion: se programaron pero no se enviaron");
            }

            List<Long> porEnviar = jdbc.queryForList("""
                            SELECT id FROM recordatorios
                            WHERE (estado = 'PENDIENTE' OR (estado = 'FALLIDO' AND intentos < :maxIntentos))
                              AND programado_para <= CAST(:hoy AS date)
                              AND programado_para >= CAST(:hoy AS date) - CAST(:gracia AS integer)
                            ORDER BY programado_para, id
                            LIMIT :limite
                            """,
                    new MapSqlParameterSource("hoy", hoy)
                            .addValue("maxIntentos", MAXIMO_INTENTOS)
                            .addValue("gracia", DIAS_GRACIA_ENVIO)
                            .addValue("limite", MAXIMO_POR_CORRIDA),
                    Long.class);

            int enviados = 0;
            int fallidos = 0;
            int canceladosEnvio = 0;
            for (Long id : porEnviar) {
                Resultado r = tx.execute(st -> procesar(id));
                if (r == Resultado.ENVIADO) {
                    enviados++;
                } else if (r == Resultado.FALLIDO) {
                    fallidos++;
                } else if (r == Resultado.CANCELADO) {
                    canceladosEnvio++;
                }
            }

            String aviso = porEnviar.size() == MAXIMO_POR_CORRIDA
                    ? "Se alcanzo el tope de " + MAXIMO_POR_CORRIDA + " mensajes; el resto sale en la siguiente corrida"
                    : null;
            return new ResumenEjecucion(hoy, enviador.nombre(), n(generados), enviados, fallidos,
                    n(cancelados) + canceladosEnvio, aviso);
        } finally {
            corriendo.unlock();
        }
    }

    /**
     * Solo programa y limpia, sin mandar nada (lo usan los datos de demo, para
     * no mandar WhatsApp reales al cargar datos).
     */
    public ResumenEjecucion programar() {
        if (!corriendo.tryLock()) {
            throw new ConflictoException("Ya hay una corrida de recordatorios en curso");
        }
        try {
            LocalDate hoy = configuracion.obtenerEntidad().hoy();
            Integer generados = tx.execute(st -> generar(hoy));
            Integer cancelados = tx.execute(st -> cancelarLoQueYaNoAplica(hoy));
            return new ResumenEjecucion(hoy, enviador.nombre(), n(generados), 0, 0, n(cancelados),
                    "Solo se programaron; no se envio nada");
        } finally {
            corriendo.unlock();
        }
    }

    private static int n(Integer v) {
        return v == null ? 0 : v;
    }

    /** Nota viva x regla activa, desde hoy en adelante. Las fechas ya pasadas no se programan. */
    int generar(LocalDate hoy) {
        return jdbc.update("""
                INSERT INTO recordatorios (nota_id, politica_id, programado_para, estado, intentos)
                SELECT n.id, p.id, n.fecha_vencimiento + p.dias_offset, 'PENDIENTE', 0
                FROM notas n
                CROSS JOIN politica_recordatorios p
                WHERE p.activo
                  AND n.estado <> 'CANCELADA'
                  AND n.saldo > 0
                  AND n.fecha_vencimiento + p.dias_offset >= CAST(:hoy AS date)
                ON CONFLICT (nota_id, politica_id) DO NOTHING
                """, new MapSqlParameterSource("hoy", hoy));
    }

    private int cancelarLoQueYaNoAplica(LocalDate hoy) {
        MapSqlParameterSource p = new MapSqlParameterSource("hoy", hoy).addValue("gracia", DIAS_GRACIA_ENVIO);

        int notaMuerta = jdbc.update("""
                UPDATE recordatorios r
                SET estado = 'CANCELADO', error = 'La nota ya se pago o se cancelo'
                FROM notas n
                WHERE n.id = r.nota_id
                  AND r.estado IN ('PENDIENTE', 'FALLIDO')
                  AND (n.estado = 'CANCELADA' OR n.saldo = 0)
                """, p);

        int tarde = jdbc.update("""
                UPDATE recordatorios
                SET estado = 'CANCELADO', error = 'No se envio a tiempo'
                WHERE estado IN ('PENDIENTE', 'FALLIDO')
                  AND programado_para < CAST(:hoy AS date) - CAST(:gracia AS integer)
                """, p);

        int agotados = jdbc.update("""
                UPDATE recordatorios
                SET estado = 'CANCELADO', error = COALESCE(error, '') || ' (se agotaron los intentos)'
                WHERE estado = 'FALLIDO' AND intentos >= :maxIntentos
                """, p.addValue("maxIntentos", MAXIMO_INTENTOS));

        return notaMuerta + tarde + agotados;
    }

    private enum Resultado { ENVIADO, FALLIDO, CANCELADO, OMITIDO }

    /** Un recordatorio, en su propia transaccion. */
    private Resultado procesar(Long id) {
        Recordatorio r = repository.findById(id).orElse(null);
        if (r == null || r.getEstado() == EstadoRecordatorio.ENVIADO || r.getEstado() == EstadoRecordatorio.CANCELADO) {
            return Resultado.OMITIDO;
        }

        Nota nota = r.getNota();
        Cliente cliente = nota.getCliente();

        if (!nota.estaViva()) {
            return cancelar(r, "La nota ya se pago o se cancelo");
        }
        if (!Boolean.TRUE.equals(cliente.getActivo())) {
            return cancelar(r, "Cliente dado de baja");
        }
        if (!Boolean.TRUE.equals(cliente.getAceptaWhatsapp())) {
            return cancelar(r, "El cliente no ha aceptado recibir WhatsApp");
        }
        if (cliente.getTelefonoWhatsapp() == null || cliente.getTelefonoWhatsapp().isBlank()) {
            return cancelar(r, "El cliente no tiene telefono de WhatsApp");
        }

        String mensaje = PlantillaMensaje.llenar(r.getPolitica().getPlantilla(), cliente.getNombreNegocio(),
                nota.getFolioOrigen(), nota.getSaldo(), nota.getFechaVencimiento());

        r.setIntentos(r.getIntentos() + 1);
        r.setTelefonoDestino(cliente.getTelefonoWhatsapp());
        r.setMensaje(mensaje);

        EnviadorMensajes.ResultadoEnvio envio = enviador.enviar(cliente.getTelefonoWhatsapp(), mensaje);
        if (envio.ok()) {
            r.setEstado(EstadoRecordatorio.ENVIADO);
            r.setEnviadoEn(Instant.now());
            r.setMensajeProveedorId(recortar(envio.idProveedor(), 64));
            r.setError(null);
            return Resultado.ENVIADO;
        }
        r.setEstado(EstadoRecordatorio.FALLIDO);
        r.setError(recortar(envio.error(), 500));
        log.warn("No se pudo mandar el recordatorio {}: {}", id, envio.error());
        return Resultado.FALLIDO;
    }

    private static Resultado cancelar(Recordatorio r, String motivo) {
        r.setEstado(EstadoRecordatorio.CANCELADO);
        r.setError(motivo);
        return Resultado.CANCELADO;
    }

    private static String recortar(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    // ------------------------------------------------------------------
    // Consultas
    // ------------------------------------------------------------------

    /** fecha = programado para ese dia. */
    @Transactional(readOnly = true)
    public Pagina<RecordatorioDto> listar(EstadoRecordatorio estado, LocalDate fecha, Long clienteId, Long notaId,
                                          int pagina, int tamano) {
        int p = Math.max(pagina, 0);
        int t = Math.min(Math.max(tamano, 1), TAMANO_MAXIMO);

        FiltroSql f = new FiltroSql()
                .si(estado == null ? null : estado.name(), "r.estado = :estado", "estado")
                .si(fecha, "r.programado_para = :fecha", "fecha")
                .si(clienteId, "n.cliente_id = :clienteId", "clienteId")
                .si(notaId, "r.nota_id = :notaId", "notaId");

        String desde = """
                FROM recordatorios r
                JOIN notas n ON n.id = r.nota_id
                JOIN clientes c ON c.id = n.cliente_id
                JOIN politica_recordatorios pr ON pr.id = r.politica_id
                """;

        Long total = jdbc.queryForObject("SELECT COUNT(*) " + desde + f.where(), f.params(), Long.class);

        String sql = """
                SELECT r.id, r.nota_id, n.folio_origen, n.cliente_id, c.nombre_negocio, r.politica_id,
                       pr.dias_offset, r.programado_para, r.estado, r.intentos, r.telefono_destino, r.mensaje,
                       r.mensaje_proveedor_id, r.error, r.enviado_en
                """ + desde + f.where()
                // Primero lo de hoy y lo ya pasado (lo mas reciente arriba); al final lo que
                // todavia no toca, el mas proximo primero. Asi la pantalla abre con lo de hoy.
                + """

                ORDER BY (r.programado_para > CAST(:hoy AS date)),
                         CASE WHEN r.programado_para <= CAST(:hoy AS date) THEN r.programado_para END DESC,
                         r.programado_para ASC,
                         r.id DESC
                LIMIT :tamano OFFSET :offset
                """;
        f.param("hoy", configuracion.obtenerEntidad().hoy());
        f.param("tamano", t).param("offset", (long) p * t);

        List<RecordatorioDto> contenido = jdbc.query(sql, f.params(), (rs, i) -> {
            OffsetDateTime enviado = rs.getObject("enviado_en", OffsetDateTime.class);
            return new RecordatorioDto(
                    rs.getLong("id"),
                    rs.getLong("nota_id"),
                    rs.getString("folio_origen"),
                    rs.getLong("cliente_id"),
                    rs.getString("nombre_negocio"),
                    rs.getLong("politica_id"),
                    rs.getInt("dias_offset"),
                    rs.getObject("programado_para", LocalDate.class),
                    EstadoRecordatorio.valueOf(rs.getString("estado")),
                    rs.getInt("intentos"),
                    rs.getString("telefono_destino"),
                    rs.getString("mensaje"),
                    rs.getString("mensaje_proveedor_id"),
                    rs.getString("error"),
                    enviado == null ? null : enviado.toInstant());
        });
        return Pagina.of(contenido, p, t, total == null ? 0 : total);
    }

    // ------------------------------------------------------------------
    // Politica
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<PoliticaDto> politicas() {
        return politicas.findAll().stream()
                .sorted((a, b) -> Integer.compare(a.getDiasOffset(), b.getDiasOffset()))
                .map(PoliticaDto::from)
                .toList();
    }

    @Transactional
    public PoliticaDto crearPolitica(PoliticaDto dto) {
        validarOffsetLibre(dto.diasOffset(), null);
        PoliticaRecordatorio p = new PoliticaRecordatorio();
        aplicar(p, dto);
        return PoliticaDto.from(politicas.save(p));
    }

    /**
     * Desactivar una regla (activo = false) no borra lo ya enviado; sus
     * pendientes se cancelan para que no salgan.
     */
    @Transactional
    public PoliticaDto actualizarPolitica(Long id, PoliticaDto dto) {
        PoliticaRecordatorio p = politicas.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la regla " + id));
        validarOffsetLibre(dto.diasOffset(), id);
        boolean seDesactiva = Boolean.TRUE.equals(p.getActivo()) && !dto.activo();
        aplicar(p, dto);
        politicas.saveAndFlush(p);
        if (seDesactiva) {
            jdbc.update("""
                    UPDATE recordatorios SET estado = 'CANCELADO', error = 'Regla desactivada'
                    WHERE politica_id = :id AND estado IN ('PENDIENTE', 'FALLIDO')
                    """, new MapSqlParameterSource("id", id));
        }
        return PoliticaDto.from(p);
    }

    private void validarOffsetLibre(int offset, Long idPropio) {
        boolean ocupado = politicas.findAll().stream()
                .anyMatch(x -> x.getDiasOffset() == offset && !x.getId().equals(idPropio));
        if (ocupado) {
            throw new ConflictoException("Ya hay una regla para " + offset + " dias");
        }
    }

    private static void aplicar(PoliticaRecordatorio p, PoliticaDto dto) {
        p.setDiasOffset(dto.diasOffset());
        p.setPlantilla(dto.plantilla().trim());
        p.setNombrePlantillaWhatsapp(dto.nombrePlantillaWhatsapp() == null || dto.nombrePlantillaWhatsapp().isBlank()
                ? null : dto.nombrePlantillaWhatsapp().trim());
        p.setActivo(dto.activo());
    }
}
