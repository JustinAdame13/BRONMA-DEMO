package mx.bronma.cobranza.riesgo;

import mx.bronma.cobranza.Enums.Enums.ModeloAyax;
import mx.bronma.cobranza.cliente.ClienteRepository;
import mx.bronma.cobranza.common.Pagina;
import mx.bronma.cobranza.configuracion.Configuracion;
import mx.bronma.cobranza.configuracion.ConfiguracionService;
import mx.bronma.cobranza.ia.ClienteIa;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Ayax: califica el riesgo de impago de cada cliente y recomienda una accion.
 *
 * Siempre corre el modelo BASE (reglas de {@link MotorAyax}). Si hay IA
 * configurada, ademas manda a la IA los clientes que deben dinero (los mas
 * riesgosos primero) y guarda su opinion como modelo IA. Si la IA falla, no
 * pasa nada: queda la evaluacion BASE.
 *
 * Una evaluacion por cliente + dia + modelo: recalcular el mismo dia reemplaza.
 * Ayax recomienda; no cambia el credito del cliente por su cuenta.
 */
@Service
public class RiesgoService {

    private static final Logger log = LoggerFactory.getLogger(RiesgoService.class);

    /** Para acotar el costo: la IA solo ve a los N clientes con deuda mas riesgosos. */
    static final int MAXIMO_CLIENTES_IA = 100;
    private static final int LOTE_IA = 20;
    private static final int TAMANO_MAXIMO = 100;

    private static final String SISTEMA_IA = """
            Eres Ayax, analista de credito de una distribuidora de lacteos en Mexico que vende a credito a \
            tienditas y abarrotes. Recibes senales del historial de pago de cada cliente y una calificacion \
            previa hecha con reglas. Tu trabajo es dar tu propia calificacion de riesgo de impago.
            Responde UNICAMENTE con un arreglo JSON, sin texto antes ni despues. Un objeto por cliente:
            {"clienteId": numero, "score": 0-100 (100 = no va a pagar), "nivel": "BAJO"|"MEDIO"|"ALTO",
             "accion": "SIN_ACCION"|"COBRAR_PROXIMA_VISITA"|"OFRECER_DESCUENTO_PRONTO_PAGO"|"REDUCIR_CREDITO"|"SUSPENDER_CREDITO",
             "explicacion": "maximo 200 caracteres, en espanol, dirigida al cobrador"}
            Criterio: BAJO < 35 <= MEDIO < 65 <= ALTO. Se prudente: suspender credito es para casos claros.""";

    private final RiesgoConsultas consultas;
    private final EvaluacionRiesgoRepository repository;
    private final ClienteRepository clientes;
    private final ConfiguracionService configuracion;
    private final ClienteIa clienteIa;
    private final TransactionTemplate tx;

    public RiesgoService(RiesgoConsultas consultas,
                         EvaluacionRiesgoRepository repository,
                         ClienteRepository clientes,
                         ConfiguracionService configuracion,
                         ClienteIa clienteIa,
                         TransactionTemplate tx) {
        this.consultas = consultas;
        this.repository = repository;
        this.clientes = clientes;
        this.configuracion = configuracion;
        this.clienteIa = clienteIa;
        this.tx = tx;
    }

    /** Lo que regresa POST /riesgo/evaluar. */
    public record ResumenEvaluacion(
            LocalDate fecha,
            int evaluados,
            Map<NivelRiesgo, Integer> porNivel,
            boolean iaDisponible,
            int evaluadosConIa,
            String avisoIa) {
    }

    // ------------------------------------------------------------------
    // Evaluar
    // ------------------------------------------------------------------

    /** Todos los dias a las 6:15 am de Torreon, antes de que salgan las rutas. */
    @Scheduled(cron = "0 15 6 * * *", zone = "America/Mexico_City")
    public void evaluarDiario() {
        try {
            ResumenEvaluacion r = evaluar(null, true);
            log.info("Ayax diario: {} clientes evaluados, {} con IA", r.evaluados(), r.evaluadosConIa());
        } catch (Exception e) {
            log.error("Fallo la evaluacion diaria de Ayax", e);
        }
    }

    /**
     * @param clienteId null = todos los clientes activos con historial.
     * @param usarIa    false = solo BASE aunque haya IA configurada.
     *
     * Sin @Transactional a proposito: las llamadas a la IA pueden tardar
     * segundos y no deben tener una transaccion abierta. Cada guardado va en
     * su propia transaccion corta.
     */
    public ResumenEvaluacion evaluar(Long clienteId, boolean usarIa) {

        Configuracion cfg = configuracion.obtenerEntidad();
        LocalDate hoy = cfg.hoy();

        List<SenalesCliente> senales = consultas.senales(hoy, cfg.getZonaHoraria(), cfg.getLimiteCreditoDefault(), clienteId);

        // 1. BASE, siempre
        Map<Long, ResultadoAyax> base = new HashMap<>();
        for (SenalesCliente s : senales) {
            base.put(s.clienteId(), MotorAyax.evaluar(s));
        }
        tx.executeWithoutResult(st -> guardar(senales, base, ModeloAyax.BASE, hoy));

        Map<NivelRiesgo, Integer> porNivel = new EnumMap<>(NivelRiesgo.class);
        for (NivelRiesgo n : NivelRiesgo.values()) {
            porNivel.put(n, 0);
        }
        base.values().forEach(r -> porNivel.merge(r.nivel(), 1, Integer::sum));

        // 2. IA, si se puede
        boolean iaDisponible = clienteIa.disponible();
        if (!usarIa || !iaDisponible) {
            return new ResumenEvaluacion(hoy, senales.size(), porNivel, iaDisponible, 0,
                    iaDisponible ? "IA omitida a peticion" : "IA apagada: solo modelo BASE");
        }

        List<SenalesCliente> candidatos = senales.stream()
                .filter(s -> s.saldo().signum() > 0)
                .sorted(Comparator.comparingInt((SenalesCliente s) -> base.get(s.clienteId()).score()).reversed())
                .limit(MAXIMO_CLIENTES_IA)
                .toList();

        Map<Long, ResultadoAyax> ia = new HashMap<>();
        int lotesFallidos = 0;
        for (int i = 0; i < candidatos.size(); i += LOTE_IA) {
            List<SenalesCliente> lote = candidatos.subList(i, Math.min(i + LOTE_IA, candidatos.size()));
            Map<Long, ResultadoAyax> respuesta = consultarIa(lote, base);
            if (respuesta.isEmpty()) {
                lotesFallidos++;
            }
            ia.putAll(respuesta);
        }

        if (!ia.isEmpty()) {
            List<SenalesCliente> conIa = candidatos.stream().filter(s -> ia.containsKey(s.clienteId())).toList();
            tx.executeWithoutResult(st -> guardar(conIa, ia, ModeloAyax.IA, hoy));
        }

        String aviso = lotesFallidos == 0 ? null
                : lotesFallidos + " lote(s) de la IA fallaron; esos clientes se quedan con BASE (ver /configuracion/ia/estado)";
        return new ResumenEvaluacion(hoy, senales.size(), porNivel, true, ia.size(), aviso);
    }

    /** Upsert: si ya hay evaluacion de hoy con ese modelo se actualiza; si no, se crea. */
    private void guardar(List<SenalesCliente> senales, Map<Long, ResultadoAyax> resultados,
                         ModeloAyax modelo, LocalDate hoy) {

        Map<Long, EvaluacionRiesgo> deHoy = repository.findByFechaCalculoAndModeloOrderByScoreDesc(hoy, modelo)
                .stream()
                .collect(Collectors.toMap(e -> e.getCliente().getId(), e -> e, (a, b) -> a));

        List<EvaluacionRiesgo> aGuardar = new ArrayList<>();
        for (SenalesCliente s : senales) {
            ResultadoAyax r = resultados.get(s.clienteId());
            if (r == null) {
                continue;
            }
            EvaluacionRiesgo e = deHoy.get(s.clienteId());
            if (e == null) {
                e = new EvaluacionRiesgo();
                e.setCliente(clientes.getReferenceById(s.clienteId()));
                e.setFechaCalculo(hoy);
                e.setModelo(modelo);
            }
            e.setScore(r.score());
            e.setNivel(r.nivel());
            e.setAccionRecomendada(r.accion());
            e.setExplicacion(r.explicacion());
            // Topes de las columnas NUMERIC: un valor absurdo no debe tumbar el guardado
            e.setDiasPromedioRetraso(MotorAyax.decimal(acotar(s.diasPromedioRetraso(), 0, 9999), 2));
            e.setTendenciaDeuda(MotorAyax.decimal(acotar(s.tendenciaDeuda(), -1, 999), 4));
            e.setUsoCredito(MotorAyax.decimal(acotar(s.usoCredito(), 0, 999), 4));
            e.setFrecuenciaCompraDias(MotorAyax.decimal(acotar(s.frecuenciaCompraDias(), 0, 9999), 2));
            e.setSaldoVencido(s.saldoVencido());
            aGuardar.add(e);
        }
        repository.saveAll(aGuardar);
    }

    private static Double acotar(Double v, double min, double max) {
        return v == null ? null : Math.max(min, Math.min(max, v));
    }

    /** Un lote a la IA. Lo que no se entienda de la respuesta se descarta (queda BASE). */
    private Map<Long, ResultadoAyax> consultarIa(List<SenalesCliente> lote, Map<Long, ResultadoAyax> base) {

        StringBuilder prompt = new StringBuilder("Clientes a calificar (montos en pesos):\n");
        for (SenalesCliente s : lote) {
            ResultadoAyax b = base.get(s.clienteId());
            prompt.append(String.format(Locale.ROOT,
                    "- clienteId=%d; saldo=%.2f; saldoVencido=%.2f; diasAtrasoMax=%d; diasPromedioRetraso=%s; "
                            + "usoCredito=%s; tendenciaDeuda30d=%s; frecuenciaCompraDias=%s; diasSinComprar=%s; "
                            + "reglas: score=%d nivel=%s (%s)%n",
                    s.clienteId(), s.saldo(), s.saldoVencido(), s.diasAtrasoMax(),
                    fmt(s.diasPromedioRetraso()), fmt(s.usoCredito()), fmt(s.tendenciaDeuda()),
                    fmt(s.frecuenciaCompraDias()), s.diasSinComprar() == null ? "n/d" : s.diasSinComprar(),
                    b.score(), b.nivel(), b.explicacion()));
        }

        Optional<List<Object>> respuesta = clienteIa.completarJsonArreglo(SISTEMA_IA, prompt.toString(), 4000);
        if (respuesta.isEmpty()) {
            return Map.of();
        }

        Map<Long, SenalesCliente> delLote = lote.stream()
                .collect(Collectors.toMap(SenalesCliente::clienteId, s -> s));

        Map<Long, ResultadoAyax> resultado = new HashMap<>();
        for (Object o : respuesta.get()) {
            if (!(o instanceof Map<?, ?> m)) {
                continue;
            }
            Double id = ClienteIa.numero(m.get("clienteId"));
            Double score = ClienteIa.numero(m.get("score"));
            if (id == null || score == null || !delLote.containsKey(id.longValue())) {
                continue; // la IA invento un cliente o no mando score
            }
            int sc = (int) Math.max(0, Math.min(100, Math.round(score)));

            NivelRiesgo nivel = enumO(NivelRiesgo.class, m.get("nivel"),
                    sc >= MotorAyax.LIMITE_ALTO ? NivelRiesgo.ALTO
                            : sc >= MotorAyax.LIMITE_MEDIO ? NivelRiesgo.MEDIO : NivelRiesgo.BAJO);
            SenalesCliente s = delLote.get(id.longValue());
            AccionRecomendada accion = enumO(AccionRecomendada.class, m.get("accion"),
                    MotorAyax.accion(nivel, s, s.saldoVencido().signum() > 0));

            String explicacion = m.get("explicacion") == null ? "" : m.get("explicacion").toString().trim();
            if (explicacion.length() > 500) {
                explicacion = explicacion.substring(0, 500);
            }
            resultado.put(id.longValue(), new ResultadoAyax(sc, nivel, accion,
                    explicacion.isEmpty() ? base.get(id.longValue()).explicacion() : explicacion));
        }
        return resultado;
    }

    private static String fmt(Double v) {
        return v == null ? "n/d" : String.format(Locale.ROOT, "%.2f", v);
    }

    private static <E extends Enum<E>> E enumO(Class<E> tipo, Object valor, E porDefecto) {
        if (valor == null) {
            return porDefecto;
        }
        try {
            return Enum.valueOf(tipo, valor.toString().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return porDefecto;
        }
    }

    // ------------------------------------------------------------------
    // Lectura
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Pagina<EvaluacionDto> vigentes(NivelRiesgo nivel, Long rutaId, int pagina, int tamano) {
        int p = Math.max(pagina, 0);
        int t = Math.min(Math.max(tamano, 1), TAMANO_MAXIMO);
        return consultas.vigentes(nivel, rutaId, p, t);
    }

    @Transactional(readOnly = true)
    public List<EvaluacionDto> historial(Long clienteId) {
        return consultas.historial(clienteId);
    }

    @Transactional(readOnly = true)
    public EvaluacionDto vigenteDe(Long clienteId) {
        return consultas.vigenteDe(clienteId);
    }
}
