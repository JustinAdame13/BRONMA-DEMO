package mx.bronma.cobranza.cliente;

import mx.bronma.cobranza.common.FiltroSql;
import mx.bronma.cobranza.common.Pagina;
import mx.bronma.cobranza.pago.SaldoAFavorService;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Consultas de lectura de clientes, en SQL directo.
 *
 * Por que no con JPA: la lista lleva cuatro filtros opcionales y dos saldos
 * calculados desde la tabla notas. Armar eso con metodos derivados daria una
 * combinatoria de nombres impracticable, y traer las notas por cliente seria
 * el N+1 que el roadmap prohibe. Para eso existe FiltroSql en common.
 */
@Repository
public class ClienteConsultas {

    /**
     * Saldos de las notas vivas, agrupados por cliente, como subconsulta.
     * Vivas = no canceladas y con saldo pendiente.
     * Vencido = lo que ya paso su fecha de vencimiento.
     * :hoy es el "hoy" del negocio (zona de configuracion). CURRENT_DATE no
     * sirve: el servidor de la base esta en UTC y de 6 pm a medianoche en
     * Torreon ya seria "manana".
     */
    private static final String JOIN_SALDOS = """
            LEFT JOIN (
                SELECT cliente_id,
                       SUM(saldo) AS saldo,
                       SUM(CASE WHEN fecha_vencimiento < CAST(:hoy AS date) THEN saldo ELSE 0 END) AS saldo_vencido
                FROM notas
                WHERE estado <> 'CANCELADA' AND saldo > 0
                GROUP BY cliente_id
            ) s ON s.cliente_id = c.id
            """
            + "LEFT JOIN (SELECT cliente_id, SUM(remanente) AS saldo_a_favor FROM ("
            + SaldoAFavorService.REMANENTE_POR_PAGO
            + ") rp GROUP BY cliente_id) sf ON sf.cliente_id = c.id\n";

    private final NamedParameterJdbcTemplate jdbc;

    public ClienteConsultas(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Pagina<ClienteResumenDto> buscar(Long rutaId, EstadoCredito estadoCredito,
                                            String q, Boolean sinUbicacion, Boolean activo,
                                            LocalDate hoy, int pagina, int tamano) {

        FiltroSql f = new FiltroSql()
                .si(rutaId, "c.ruta_id = :rutaId", "rutaId")
                .si(activo, "c.activo = :activo", "activo")
                .si(estadoCredito == null ? null : estadoCredito.name(),
                        "c.estado_credito = :estadoCredito", "estadoCredito")
                .si(q == null || q.isBlank() ? null : "%" + q.trim() + "%",
                        "(c.nombre_negocio ILIKE :q OR c.codigo_origen ILIKE :q)", "q");

        // Solo el true filtra: sinUbicacion=false no significa "los ubicados".
        if (Boolean.TRUE.equals(sinUbicacion)) {
            f.siempre("c.latitud IS NULL");
        }
        f.param("hoy", hoy);

        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM clientes c" + f.where(), f.params(), Long.class);

        // OJO: el text block del ORDER BY empieza con una linea en blanco a proposito.
        // f.where() no termina en salto de linea; sin ella quedaba ":rutaIdORDER BY".
        String sql = """
                SELECT c.id, c.codigo_origen, c.nombre_negocio,
                       c.ruta_id, r.nombre AS ruta_nombre,
                       c.telefono_whatsapp, c.estado_credito,
                       COALESCE(s.saldo, 0)         AS saldo,
                       COALESCE(s.saldo_vencido, 0) AS saldo_vencido,
                       COALESCE(sf.saldo_a_favor, 0) AS saldo_a_favor,
                       c.latitud, c.longitud, c.orden_visita, c.activo
                FROM clientes c
                LEFT JOIN rutas r ON r.id = c.ruta_id
                """ + JOIN_SALDOS + f.where() + """

                ORDER BY c.orden_visita NULLS LAST, c.nombre_negocio
                LIMIT :tamano OFFSET :offset
                """;

        f.param("tamano", tamano).param("offset", (long) pagina * tamano);

        List<ClienteResumenDto> contenido = jdbc.query(sql, f.params(), (rs, i) -> new ClienteResumenDto(
                rs.getLong("id"),
                rs.getString("codigo_origen"),
                rs.getString("nombre_negocio"),
                (Long) rs.getObject("ruta_id"),
                rs.getString("ruta_nombre"),
                rs.getString("telefono_whatsapp"),
                EstadoCredito.valueOf(rs.getString("estado_credito")),
                rs.getBigDecimal("saldo"),
                rs.getBigDecimal("saldo_vencido"),
                rs.getBigDecimal("saldo_a_favor"),
                (Double) rs.getObject("latitud"),
                (Double) rs.getObject("longitud"),
                (Integer) rs.getObject("orden_visita"),
                rs.getBoolean("activo")));

        return Pagina.of(contenido, pagina, tamano, total == null ? 0 : total);
    }

    /** Los mismos saldos pero de un solo cliente, para el detalle. */
    public SaldoCliente saldosDe(Long clienteId, LocalDate hoy) {
        String sql = """
                SELECT COALESCE(SUM(saldo), 0) AS saldo,
                       COALESCE(SUM(CASE WHEN fecha_vencimiento < CAST(:hoy AS date) THEN saldo ELSE 0 END), 0) AS saldo_vencido,
                """
                + "(SELECT COALESCE(SUM(remanente), 0) FROM (" + SaldoAFavorService.REMANENTE_POR_PAGO
                + ") rp WHERE rp.cliente_id = :id) AS saldo_a_favor\n"
                + """
                FROM notas
                WHERE cliente_id = :id AND estado <> 'CANCELADA' AND saldo > 0
                """;

        return jdbc.queryForObject(sql, new MapSqlParameterSource("id", clienteId).addValue("hoy", hoy),
                (rs, i) -> new SaldoCliente(rs.getBigDecimal("saldo"), rs.getBigDecimal("saldo_vencido"),
                        rs.getBigDecimal("saldo_a_favor")));
    }
}
