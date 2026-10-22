package mx.bronma.cobranza.dashboard;

import mx.bronma.cobranza.cobro.CarteraCliente;
import mx.bronma.cobranza.cobro.CarteraConsultas;
import mx.bronma.cobranza.common.ReglaNegocioException;
import mx.bronma.cobranza.configuracion.Configuracion;
import mx.bronma.cobranza.configuracion.ConfiguracionService;
import mx.bronma.cobranza.pago.SaldoAFavorService;
import mx.bronma.cobranza.riesgo.NivelRiesgo;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DashboardService {

    /** Ventana para atribuir un pago a un recordatorio. */
    static final int DIAS_ATRIBUCION_RECORDATORIO = 7;

    private static final List<String> RANGOS = List.of("AL_CORRIENTE", "VENCIDO_1_30", "VENCIDO_31_60", "VENCIDO_MAS_60");

    private final NamedParameterJdbcTemplate jdbc;
    private final CarteraConsultas cartera;
    private final ConfiguracionService configuracion;

    public DashboardService(NamedParameterJdbcTemplate jdbc, CarteraConsultas cartera,
                            ConfiguracionService configuracion) {
        this.jdbc = jdbc;
        this.cartera = cartera;
        this.configuracion = configuracion;
    }

    /** desde/hasta: dias del negocio, ambos inclusive. Default: los ultimos 30 dias. */
    @Transactional(readOnly = true)
    public DashboardDto generar(LocalDate desde, LocalDate hasta, int top) {

        Configuracion cfg = configuracion.obtenerEntidad();
        LocalDate hoy = cfg.hoy();
        LocalDate h = hasta != null ? hasta : hoy;
        LocalDate d = desde != null ? desde : h.minusDays(29);
        if (d.isAfter(h)) {
            throw new ReglaNegocioException("'desde' no puede ser posterior a 'hasta'");
        }
        if (ChronoUnit.DAYS.between(d, h) > 366) {
            throw new ReglaNegocioException("El periodo no puede pasar de un anio");
        }
        int t = Math.min(Math.max(top, 1), 50);

        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("hoy", hoy)
                .addValue("zona", cfg.getZonaHoraria())
                .addValue("ini", d.atStartOfDay(cfg.zona()).toOffsetDateTime().withOffsetSameInstant(ZoneOffset.UTC))
                .addValue("fin", h.plusDays(1).atStartOfDay(cfg.zona()).toOffsetDateTime()
                        .withOffsetSameInstant(ZoneOffset.UTC))
                .addValue("dias", DIAS_ATRIBUCION_RECORDATORIO);

        DashboardDto.Cartera resumen = resumenCartera(p);
        List<DashboardDto.Antiguedad> antiguedad = antiguedad(p);
        DashboardDto.Cobranza cobranza = cobranza(p);

        // Todo lo que es "por cliente" sale de la misma consulta que la lista de cobro
        List<CarteraCliente> clientes = cartera.clientes(null, null, hoy, 0);

        List<DashboardDto.Deudor> top10 = clientes.stream()
                .filter(c -> c.saldo().signum() > 0)
                .sorted(Comparator.comparing(CarteraCliente::saldo).reversed())
                .limit(t)
                .map(c -> new DashboardDto.Deudor(c.clienteId(), c.nombreNegocio(), c.rutaId(), c.rutaNombre(),
                        c.saldo(), c.saldoVencido(), c.diasAtrasoMax(), c.nivelRiesgo(), c.prioridad().puntos()))
                .toList();

        Map<NivelRiesgo, Integer> porRiesgo = new EnumMap<>(NivelRiesgo.class);
        for (NivelRiesgo n : NivelRiesgo.values()) {
            porRiesgo.put(n, 0);
        }
        clientes.stream().filter(c -> c.nivelRiesgo() != null)
                .forEach(c -> porRiesgo.merge(c.nivelRiesgo(), 1, Integer::sum));

        return new DashboardDto(hoy, d, h, resumen, antiguedad, top10, cobranza,
                porRuta(clientes, p), porRiesgo);
    }

    private DashboardDto.Cartera resumenCartera(MapSqlParameterSource p) {
        String sql = """
                SELECT COALESCE(SUM(saldo), 0) AS total,
                       COALESCE(SUM(saldo) FILTER (WHERE fecha_vencimiento < CAST(:hoy AS date)), 0) AS vencida,
                       COUNT(DISTINCT cliente_id) AS con_saldo,
                       COUNT(DISTINCT cliente_id) FILTER (WHERE fecha_vencimiento < CAST(:hoy AS date)) AS con_vencido,
                """
                + "(SELECT COALESCE(SUM(remanente), 0) FROM (" + SaldoAFavorService.REMANENTE_POR_PAGO
                + ") rp) AS a_favor\n"
                + """
                FROM notas
                WHERE estado <> 'CANCELADA' AND saldo > 0
                """;
        return jdbc.queryForObject(sql, p, (rs, i) -> {
            BigDecimal total = rs.getBigDecimal("total");
            BigDecimal vencida = rs.getBigDecimal("vencida");
            BigDecimal porcentaje = total.signum() == 0 ? BigDecimal.ZERO
                    : vencida.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP);
            return new DashboardDto.Cartera(total, vencida, total.subtract(vencida), porcentaje,
                    rs.getInt("con_saldo"), rs.getInt("con_vencido"), rs.getBigDecimal("a_favor"));
        });
    }

    /** Siempre regresa los cuatro rangos, aunque alguno este en cero (la grafica no brinca). */
    private List<DashboardDto.Antiguedad> antiguedad(MapSqlParameterSource p) {
        Map<String, DashboardDto.Antiguedad> porRango = new LinkedHashMap<>();
        for (String r : RANGOS) {
            porRango.put(r, new DashboardDto.Antiguedad(r, BigDecimal.ZERO, 0, 0));
        }
        jdbc.query("""
                SELECT CASE
                           WHEN fecha_vencimiento >= CAST(:hoy AS date)            THEN 'AL_CORRIENTE'
                           WHEN CAST(:hoy AS date) - fecha_vencimiento <= 30       THEN 'VENCIDO_1_30'
                           WHEN CAST(:hoy AS date) - fecha_vencimiento <= 60       THEN 'VENCIDO_31_60'
                           ELSE 'VENCIDO_MAS_60'
                       END AS rango,
                       SUM(saldo) AS monto, COUNT(*) AS notas, COUNT(DISTINCT cliente_id) AS clientes
                FROM notas
                WHERE estado <> 'CANCELADA' AND saldo > 0
                GROUP BY 1
                """, p, (RowCallbackHandler) rs -> {
            String r = rs.getString("rango");
            porRango.put(r, new DashboardDto.Antiguedad(r, rs.getBigDecimal("monto"),
                    rs.getInt("notas"), rs.getInt("clientes")));
        });
        return new ArrayList<>(porRango.values());
    }

    private DashboardDto.Cobranza cobranza(MapSqlParameterSource p) {
        return jdbc.queryForObject("""
                SELECT
                    (SELECT COALESCE(SUM(monto), 0) FROM pagos
                      WHERE fecha_pago >= :ini AND fecha_pago < :fin) AS cobrado,
                    (SELECT COUNT(*) FROM pagos
                      WHERE fecha_pago >= :ini AND fecha_pago < :fin) AS pagos,
                    (SELECT COALESCE(SUM(a.monto), 0)
                       FROM aplicaciones_pago a
                       JOIN pagos pg ON pg.id = a.pago_id
                      WHERE pg.fecha_pago >= :ini AND pg.fecha_pago < :fin
                        AND EXISTS (SELECT 1 FROM recordatorios r
                                     WHERE r.nota_id = a.nota_id
                                       AND r.estado = 'ENVIADO'
                                       AND r.enviado_en <= pg.fecha_pago
                                       AND r.enviado_en >= pg.fecha_pago - make_interval(days => CAST(:dias AS integer)))
                    ) AS recuperado,
                    (SELECT COUNT(*) FROM recordatorios
                      WHERE estado = 'ENVIADO' AND enviado_en >= :ini AND enviado_en < :fin) AS enviados,
                    (SELECT SUM(a.monto * (CAST((pg.fecha_pago AT TIME ZONE CAST(:zona AS text)) AS date) - n.fecha_emision))
                            / NULLIF(SUM(a.monto), 0)
                       FROM aplicaciones_pago a
                       JOIN pagos pg ON pg.id = a.pago_id
                       JOIN notas n ON n.id = a.nota_id
                      WHERE pg.fecha_pago >= :ini AND pg.fecha_pago < :fin) AS dias_cobro
                """, p, (rs, i) -> {
            BigDecimal dias = rs.getBigDecimal("dias_cobro");
            return new DashboardDto.Cobranza(
                    rs.getBigDecimal("cobrado"),
                    rs.getInt("pagos"),
                    rs.getBigDecimal("recuperado"),
                    rs.getInt("enviados"),
                    dias == null ? null : dias.setScale(1, RoundingMode.HALF_UP));
        });
    }

    private List<DashboardDto.PorRuta> porRuta(List<CarteraCliente> clientes, MapSqlParameterSource p) {

        Map<Long, BigDecimal> cobrado = new HashMap<>();
        jdbc.query("""
                SELECT c.ruta_id, SUM(pg.monto) AS cobrado
                FROM pagos pg
                JOIN clientes c ON c.id = pg.cliente_id
                WHERE pg.fecha_pago >= :ini AND pg.fecha_pago < :fin AND c.ruta_id IS NOT NULL
                GROUP BY c.ruta_id
                """, p, (RowCallbackHandler) rs -> {
            cobrado.put(rs.getLong("ruta_id"), rs.getBigDecimal("cobrado"));
        });

        // Agrupado en memoria: los clientes ya vienen con su ruta y saldos
        Map<Long, Acumulado> porRuta = new LinkedHashMap<>();
        for (CarteraCliente c : clientes) {
            if (c.rutaId() == null) {
                continue;
            }
            Acumulado a = porRuta.computeIfAbsent(c.rutaId(), k -> new Acumulado(c.rutaNombre(), c.rutaColor()));
            a.cartera = a.cartera.add(c.saldo());
            a.vencido = a.vencido.add(c.saldoVencido());
            if (c.saldo().signum() > 0) {
                a.clientes++;
            }
        }

        List<DashboardDto.PorRuta> lista = new ArrayList<>();
        porRuta.forEach((id, a) -> lista.add(new DashboardDto.PorRuta(id, a.nombre, a.color, a.cartera, a.vencido,
                a.clientes, cobrado.getOrDefault(id, BigDecimal.ZERO))));
        lista.sort(Comparator.comparing(DashboardDto.PorRuta::vencido).reversed());
        return lista;
    }

    private static final class Acumulado {
        final String nombre;
        final String color;
        BigDecimal cartera = BigDecimal.ZERO;
        BigDecimal vencido = BigDecimal.ZERO;
        int clientes;

        Acumulado(String nombre, String color) {
            this.nombre = nombre;
            this.color = color;
        }
    }
}
