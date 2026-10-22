package mx.bronma.cobranza.riesgo;

import mx.bronma.cobranza.common.FiltroSql;
import mx.bronma.cobranza.common.Pagina;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Consultas de Ayax en SQL directo.
 *
 * Las senales de TODOS los clientes salen de una sola consulta con CTEs:
 * evaluar 500 tienditas son 1 viaje a la base, no 500.
 */
@Repository
public class RiesgoConsultas {

    /** Ventana de historial que mira Ayax. */
    static final int DIAS_HISTORIAL = 180;

    private static final String SENALES = """
            WITH
            -- Lo que ya pago: cuantos dias tarde, ponderado por monto
            pagado AS (
                SELECT n.cliente_id,
                       SUM(a.monto * GREATEST(0, CAST((p.fecha_pago AT TIME ZONE CAST(:zona AS text)) AS date)
                                                 - n.fecha_vencimiento)) AS suma,
                       SUM(a.monto) AS peso
                FROM aplicaciones_pago a
                JOIN pagos p ON p.id = a.pago_id
                JOIN notas n ON n.id = a.nota_id
                WHERE CAST((p.fecha_pago AT TIME ZONE CAST(:zona AS text)) AS date) >= CAST(:desde AS date)
                GROUP BY n.cliente_id
            ),
            -- Lo que hoy esta vencido: cuantos dias lleva, ponderado por saldo
            vencido AS (
                SELECT cliente_id,
                       SUM(saldo * (CAST(:hoy AS date) - fecha_vencimiento)) AS suma,
                       SUM(saldo) AS saldo_vencido,
                       MAX(CAST(:hoy AS date) - fecha_vencimiento) AS dias_max
                FROM notas
                WHERE estado <> 'CANCELADA' AND saldo > 0 AND fecha_vencimiento < CAST(:hoy AS date)
                GROUP BY cliente_id
            ),
            actual AS (
                SELECT cliente_id, SUM(saldo) AS saldo
                FROM notas
                WHERE estado <> 'CANCELADA' AND saldo > 0
                GROUP BY cliente_id
            ),
            -- Saldo que tenia hace 30 dias: lo vendido hasta entonces menos lo pagado hasta entonces
            previo AS (
                SELECT n.cliente_id, SUM(n.monto - COALESCE(ap.pagado, 0)) AS saldo
                FROM notas n
                LEFT JOIN (
                    SELECT a.nota_id, SUM(a.monto) AS pagado
                    FROM aplicaciones_pago a
                    JOIN pagos p ON p.id = a.pago_id
                    WHERE CAST((p.fecha_pago AT TIME ZONE CAST(:zona AS text)) AS date) <= CAST(:hace30 AS date)
                    GROUP BY a.nota_id
                ) ap ON ap.nota_id = n.id
                WHERE n.estado <> 'CANCELADA' AND n.fecha_emision <= CAST(:hace30 AS date)
                GROUP BY n.cliente_id
            ),
            compras AS (
                SELECT cliente_id,
                       COUNT(*) FILTER (WHERE fecha_emision >= CAST(:desde AS date)) AS notas,
                       MIN(fecha_emision) FILTER (WHERE fecha_emision >= CAST(:desde AS date)) AS primera,
                       MAX(fecha_emision) AS ultima
                FROM notas
                WHERE estado <> 'CANCELADA'
                GROUP BY cliente_id
            )
            SELECT c.id, c.nombre_negocio,
                   CASE WHEN c.limite_credito > 0 THEN c.limite_credito ELSE CAST(:limiteDefault AS numeric) END AS limite,
                   COALESCE(act.saldo, 0)          AS saldo,
                   COALESCE(v.saldo_vencido, 0)    AS saldo_vencido,
                   COALESCE(v.dias_max, 0)         AS dias_max,
                   COALESCE(pg.suma, 0) + COALESCE(v.suma, 0)  AS retraso_suma,
                   COALESCE(pg.peso, 0) + COALESCE(v.saldo_vencido, 0) AS retraso_peso,
                   pv.saldo                        AS saldo_previo,
                   co.notas, co.primera, co.ultima
            FROM clientes c
            JOIN compras co ON co.cliente_id = c.id
            LEFT JOIN pagado pg  ON pg.cliente_id = c.id
            LEFT JOIN vencido v  ON v.cliente_id = c.id
            LEFT JOIN actual act ON act.cliente_id = c.id
            LEFT JOIN previo pv  ON pv.cliente_id = c.id
            WHERE c.activo
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public RiesgoConsultas(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Senales de los clientes activos que tienen al menos una nota.
     *
     * @param clienteId null = todos.
     */
    public List<SenalesCliente> senales(LocalDate hoy, String zona, BigDecimal limiteDefault, Long clienteId) {

        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("hoy", hoy)
                .addValue("zona", zona)
                .addValue("desde", hoy.minusDays(DIAS_HISTORIAL))
                .addValue("hace30", hoy.minusDays(30))
                .addValue("limiteDefault", limiteDefault == null ? BigDecimal.ZERO : limiteDefault);

        String sql = SENALES;
        if (clienteId != null) {
            sql += " AND c.id = :clienteId";
            p.addValue("clienteId", clienteId);
        }

        return jdbc.query(sql, p, (rs, i) -> {
            BigDecimal saldo = rs.getBigDecimal("saldo");
            BigDecimal limite = rs.getBigDecimal("limite");
            BigDecimal retrasoPeso = rs.getBigDecimal("retraso_peso");
            BigDecimal saldoPrevio = rs.getBigDecimal("saldo_previo");
            int notas = rs.getInt("notas");
            LocalDate primera = rs.getObject("primera", LocalDate.class);
            LocalDate ultima = rs.getObject("ultima", LocalDate.class);

            Double retraso = retrasoPeso.signum() > 0
                    ? rs.getBigDecimal("retraso_suma").doubleValue() / retrasoPeso.doubleValue()
                    : null;

            Double uso = limite != null && limite.signum() > 0
                    ? saldo.doubleValue() / limite.doubleValue()
                    : null;

            Double tendencia;
            if (saldoPrevio == null || saldoPrevio.signum() <= 0) {
                // Sin deuda hace un mes: si hoy debe, la deuda "aparecio" (crecimiento maximo)
                tendencia = saldo.signum() > 0 && saldoPrevio != null ? 1.0 : null;
            } else {
                tendencia = (saldo.doubleValue() - saldoPrevio.doubleValue()) / saldoPrevio.doubleValue();
            }

            Double frecuencia = notas >= 2 && primera != null && ultima != null
                    ? (double) (ultima.toEpochDay() - primera.toEpochDay()) / (notas - 1)
                    : null;
            Integer sinComprar = ultima == null ? null : (int) (hoy.toEpochDay() - ultima.toEpochDay());

            return new SenalesCliente(
                    rs.getLong("id"),
                    rs.getString("nombre_negocio"),
                    saldo,
                    rs.getBigDecimal("saldo_vencido"),
                    rs.getInt("dias_max"),
                    retraso,
                    tendencia,
                    uso,
                    frecuencia,
                    sinComprar);
        });
    }

    // ------------------------------------------------------------------
    // Lectura de evaluaciones
    // ------------------------------------------------------------------

    /**
     * La evaluacion vigente de cada cliente: la del dia mas reciente y, ese dia,
     * la de IA si existe (es la mas completa). Se reutiliza en lista de cobro,
     * mapa y dashboard como subconsulta LATERAL.
     */
    public static final String LATERAL_ULTIMA_EVALUACION = """
            LEFT JOIN LATERAL (
                SELECT e.id AS eval_id, e.score, e.nivel, e.accion_recomendada, e.modelo, e.fecha_calculo
                FROM evaluaciones_riesgo e
                WHERE e.cliente_id = c.id
                ORDER BY e.fecha_calculo DESC, (e.modelo = 'IA') DESC
                LIMIT 1
            ) ev ON TRUE
            """;

    private static final String SELECT_EVALUACION = """
            SELECT e.id, e.cliente_id, c.nombre_negocio, c.ruta_id, e.fecha_calculo, e.modelo, e.score, e.nivel,
                   e.dias_promedio_retraso, e.tendencia_deuda, e.uso_credito, e.frecuencia_compra_dias,
                   e.saldo_vencido, e.accion_recomendada, e.explicacion, e.generada_en
            FROM evaluaciones_riesgo e
            JOIN clientes c ON c.id = e.cliente_id
            """;

    private static final RowMapper<EvaluacionDto> MAPEO = (rs, i) -> new EvaluacionDto(
            rs.getLong("id"),
            rs.getLong("cliente_id"),
            rs.getString("nombre_negocio"),
            (Long) rs.getObject("ruta_id"),
            rs.getObject("fecha_calculo", LocalDate.class),
            mx.bronma.cobranza.Enums.Enums.ModeloAyax.valueOf(rs.getString("modelo")),
            rs.getInt("score"),
            NivelRiesgo.valueOf(rs.getString("nivel")),
            rs.getBigDecimal("dias_promedio_retraso"),
            rs.getBigDecimal("tendencia_deuda"),
            rs.getBigDecimal("uso_credito"),
            rs.getBigDecimal("frecuencia_compra_dias"),
            rs.getBigDecimal("saldo_vencido"),
            AccionRecomendada.valueOf(rs.getString("accion_recomendada")),
            rs.getString("explicacion"),
            rs.getObject("generada_en", OffsetDateTime.class).toInstant());

    /** La evaluacion vigente de cada cliente activo, la mas riesgosa primero. */
    public Pagina<EvaluacionDto> vigentes(NivelRiesgo nivel, Long rutaId, int pagina, int tamano) {

        FiltroSql f = new FiltroSql()
                .siempre("c.activo")
                .si(nivel == null ? null : nivel.name(), "e.nivel = :nivel", "nivel")
                .si(rutaId, "c.ruta_id = :rutaId", "rutaId");
        // Solo la vigente: la que eligio el LATERAL para ese cliente
        f.siempre("e.id = ev.eval_id");

        String desde = """
                FROM clientes c
                """ + LATERAL_ULTIMA_EVALUACION + """
                JOIN evaluaciones_riesgo e ON e.id = ev.eval_id
                """;

        Long total = jdbc.queryForObject("SELECT COUNT(*) " + desde + f.where(), f.params(), Long.class);

        String sql = """
                SELECT e.id, e.cliente_id, c.nombre_negocio, c.ruta_id, e.fecha_calculo, e.modelo, e.score, e.nivel,
                       e.dias_promedio_retraso, e.tendencia_deuda, e.uso_credito, e.frecuencia_compra_dias,
                       e.saldo_vencido, e.accion_recomendada, e.explicacion, e.generada_en
                """ + desde + f.where() + "\nORDER BY e.score DESC, c.nombre_negocio\nLIMIT :tamano OFFSET :offset";
        f.param("tamano", tamano).param("offset", (long) pagina * tamano);

        return Pagina.of(jdbc.query(sql, f.params(), MAPEO), pagina, tamano, total == null ? 0 : total);
    }

    /** Historial de un cliente, la mas reciente primero. */
    public List<EvaluacionDto> historial(Long clienteId) {
        return jdbc.query(SELECT_EVALUACION + " WHERE e.cliente_id = :id ORDER BY e.fecha_calculo DESC, e.modelo",
                new MapSqlParameterSource("id", clienteId), MAPEO);
    }

    /** La vigente de un cliente (para el detalle de cliente); null si nunca se ha evaluado. */
    public EvaluacionDto vigenteDe(Long clienteId) {
        List<EvaluacionDto> r = jdbc.query(SELECT_EVALUACION + """
                         WHERE e.cliente_id = :id
                        ORDER BY e.fecha_calculo DESC, (e.modelo = 'IA') DESC
                        LIMIT 1
                        """,
                new MapSqlParameterSource("id", clienteId), MAPEO);
        return r.isEmpty() ? null : r.get(0);
    }
}
