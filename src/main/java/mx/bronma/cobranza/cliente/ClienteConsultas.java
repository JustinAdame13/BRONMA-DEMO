package mx.bronma.cobranza.cliente;

import mx.bronma.cobranza.common.FiltroSql;
import mx.bronma.cobranza.common.Pagina;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

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
     */
    private static final String JOIN_SALDOS = """
            LEFT JOIN (
                SELECT cliente_id,
                       SUM(saldo) AS saldo,
                       SUM(CASE WHEN fecha_vencimiento < CURRENT_DATE THEN saldo ELSE 0 END) AS saldo_vencido
                FROM notas
                WHERE estado <> 'CANCELADA' AND saldo > 0
                GROUP BY cliente_id
            ) s ON s.cliente_id = c.id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public ClienteConsultas(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Pagina<ClienteResumenDto> buscar(Long rutaId, EstadoCredito estadoCredito,
                                            String q, Boolean sinUbicacion,
                                            int pagina, int tamano) {

        FiltroSql f = new FiltroSql()
                .si(rutaId, "c.ruta_id = :rutaId", "rutaId")
                .si(estadoCredito == null ? null : estadoCredito.name(),
                        "c.estado_credito = :estadoCredito", "estadoCredito")
                .si(q == null || q.isBlank() ? null : "%" + q.trim() + "%",
                        "(c.nombre_negocio ILIKE :q OR c.codigo_origen ILIKE :q)", "q");

        // Solo el true filtra: sinUbicacion=false no significa "los ubicados".
        if (Boolean.TRUE.equals(sinUbicacion)) {
            f.siempre("c.latitud IS NULL");
        }

        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM clientes c" + f.where(), f.params(), Long.class);

        String sql = """
                SELECT c.id, c.codigo_origen, c.nombre_negocio,
                       c.ruta_id, r.nombre AS ruta_nombre,
                       c.telefono_whatsapp, c.estado_credito,
                       COALESCE(s.saldo, 0)         AS saldo,
                       COALESCE(s.saldo_vencido, 0) AS saldo_vencido,
                       c.latitud, c.longitud, c.orden_visita
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
                (Double) rs.getObject("latitud"),
                (Double) rs.getObject("longitud"),
                (Integer) rs.getObject("orden_visita")));

        return Pagina.of(contenido, pagina, tamano, total == null ? 0 : total);
    }

    /** Los mismos saldos pero de un solo cliente, para el detalle. */
    public SaldoCliente saldosDe(Long clienteId) {
        String sql = """
                SELECT COALESCE(SUM(saldo), 0) AS saldo,
                       COALESCE(SUM(CASE WHEN fecha_vencimiento < CURRENT_DATE THEN saldo ELSE 0 END), 0) AS saldo_vencido
                FROM notas
                WHERE cliente_id = :id AND estado <> 'CANCELADA' AND saldo > 0
                """;

        return jdbc.queryForObject(sql, new MapSqlParameterSource("id", clienteId),
                (rs, i) -> new SaldoCliente(rs.getBigDecimal("saldo"), rs.getBigDecimal("saldo_vencido")));
    }
}
