package mx.bronma.cobranza.nota;

import mx.bronma.cobranza.common.FiltroSql;
import mx.bronma.cobranza.common.Pagina;
import mx.bronma.cobranza.pago.MetodoPago;
import mx.bronma.cobranza.pago.OrigenPago;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Lecturas de notas en SQL directo: filtros opcionales y "vencida" calculada
 * con el "hoy" del negocio (zona de configuracion), no con CURRENT_DATE del
 * servidor, que en Neon/Render es UTC: de 6 pm a medianoche ya seria "manana".
 */
@Repository
public class NotaConsultas {

    private static final String SELECT = """
            SELECT n.id, n.folio_origen, n.cliente_id, c.codigo_origen, c.nombre_negocio,
                   n.ruta_id, r.nombre AS ruta_nombre,
                   n.fecha_emision, n.fecha_vencimiento, n.monto, n.saldo, n.estado,
                   n.importacion_id,
                   CASE WHEN n.estado <> 'CANCELADA' AND n.saldo > 0 AND n.fecha_vencimiento < CAST(:hoy AS date)
                        THEN CAST(:hoy AS date) - n.fecha_vencimiento
                        ELSE 0 END AS dias_atraso
            FROM notas n
            JOIN clientes c ON c.id = n.cliente_id
            LEFT JOIN rutas r ON r.id = n.ruta_id
            """;

    private static final RowMapper<NotaDto> MAPEO = (rs, i) -> {
        int dias = rs.getInt("dias_atraso");
        return new NotaDto(
                rs.getLong("id"),
                rs.getString("folio_origen"),
                rs.getLong("cliente_id"),
                rs.getString("codigo_origen"),
                rs.getString("nombre_negocio"),
                (Long) rs.getObject("ruta_id"),
                rs.getString("ruta_nombre"),
                rs.getObject("fecha_emision", LocalDate.class),
                rs.getObject("fecha_vencimiento", LocalDate.class),
                rs.getBigDecimal("monto"),
                rs.getBigDecimal("saldo"),
                EstadoNota.valueOf(rs.getString("estado")),
                dias > 0,
                dias,
                (Long) rs.getObject("importacion_id"));
    };

    private final NamedParameterJdbcTemplate jdbc;

    public NotaConsultas(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * @param vivas    true = solo con saldo y no canceladas.
     * @param vencidas true = solo vivas que ya vencieron.
     * @param desde    fecha de emision minima (inclusive).
     * @param hasta    fecha de emision maxima (inclusive).
     */
    public Pagina<NotaDto> buscar(Long clienteId, Long rutaId, EstadoNota estado, Boolean vivas,
                                  Boolean vencidas, String q, LocalDate desde, LocalDate hasta,
                                  LocalDate hoy, int pagina, int tamano) {

        FiltroSql f = new FiltroSql()
                .si(clienteId, "n.cliente_id = :clienteId", "clienteId")
                .si(rutaId, "n.ruta_id = :rutaId", "rutaId")
                .si(estado == null ? null : estado.name(), "n.estado = :estado", "estado")
                .si(q == null || q.isBlank() ? null : "%" + q.trim() + "%", "n.folio_origen ILIKE :q", "q")
                .si(desde, "n.fecha_emision >= :desde", "desde")
                .si(hasta, "n.fecha_emision <= :hasta", "hasta");

        // Igual que sinUbicacion en clientes: solo el true filtra
        if (Boolean.TRUE.equals(vivas) || Boolean.TRUE.equals(vencidas)) {
            f.siempre("n.estado <> 'CANCELADA' AND n.saldo > 0");
        }
        if (Boolean.TRUE.equals(vencidas)) {
            f.siempre("n.fecha_vencimiento < CAST(:hoy AS date)");
        }
        f.param("hoy", hoy);

        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM notas n" + f.where(), f.params(), Long.class);

        // El salto de linea antes del ORDER BY es a proposito (ver ClienteConsultas)
        String sql = SELECT + f.where() + "\nORDER BY n.fecha_vencimiento, n.id\nLIMIT :tamano OFFSET :offset";
        f.param("tamano", tamano).param("offset", (long) pagina * tamano);

        List<NotaDto> contenido = jdbc.query(sql, f.params(), MAPEO);
        return Pagina.of(contenido, pagina, tamano, total == null ? 0 : total);
    }

    /** Una nota con sus datos calculados; null si no existe. */
    public NotaDto porId(Long id, LocalDate hoy) {
        List<NotaDto> r = jdbc.query(SELECT + " WHERE n.id = :id",
                new MapSqlParameterSource("id", id).addValue("hoy", hoy), MAPEO);
        return r.isEmpty() ? null : r.get(0);
    }

    /** Las notas con saldo de un cliente, la que vence antes primero (detalle de cliente). */
    public List<NotaDto> vivasDe(Long clienteId, LocalDate hoy) {
        return jdbc.query(SELECT + """
                         WHERE n.cliente_id = :clienteId AND n.estado <> 'CANCELADA' AND n.saldo > 0
                        ORDER BY n.fecha_vencimiento, n.id
                        """,
                new MapSqlParameterSource("clienteId", clienteId).addValue("hoy", hoy), MAPEO);
    }

    /** Los abonos de una nota, el mas reciente primero. */
    public List<AbonoNotaDto> abonosDe(Long notaId) {
        String sql = """
                SELECT p.id, p.fecha_pago, p.metodo, p.origen, p.referencia, a.monto
                FROM aplicaciones_pago a
                JOIN pagos p ON p.id = a.pago_id
                WHERE a.nota_id = :notaId
                ORDER BY p.fecha_pago DESC, p.id DESC
                """;
        return jdbc.query(sql, new MapSqlParameterSource("notaId", notaId), (rs, i) -> new AbonoNotaDto(
                rs.getLong("id"),
                rs.getObject("fecha_pago", OffsetDateTime.class).toInstant(),
                MetodoPago.valueOf(rs.getString("metodo")),
                OrigenPago.valueOf(rs.getString("origen")),
                rs.getString("referencia"),
                rs.getBigDecimal("monto")));
    }
}
