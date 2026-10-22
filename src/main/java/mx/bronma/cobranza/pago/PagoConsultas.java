package mx.bronma.cobranza.pago;

import mx.bronma.cobranza.common.FiltroSql;
import mx.bronma.cobranza.common.Pagina;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/** Lecturas de pagos en SQL directo (filtros opcionales, sin N+1). */
@Repository
public class PagoConsultas {

    private final NamedParameterJdbcTemplate jdbc;

    public PagoConsultas(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * @param desde inicio del rango (inclusive), ya convertido a instante con la zona del negocio.
     * @param hasta fin del rango (exclusivo).
     * @param rutaId la ruta ACTUAL del cliente.
     */
    public Pagina<PagoResumenDto> buscar(Long clienteId, Long rutaId, MetodoPago metodo, OrigenPago origen,
                                         Long registradoPorId, OffsetDateTime desde, OffsetDateTime hasta,
                                         int pagina, int tamano) {

        FiltroSql f = new FiltroSql()
                .si(clienteId, "p.cliente_id = :clienteId", "clienteId")
                .si(rutaId, "c.ruta_id = :rutaId", "rutaId")
                .si(metodo == null ? null : metodo.name(), "p.metodo = :metodo", "metodo")
                .si(origen == null ? null : origen.name(), "p.origen = :origen", "origen")
                .si(registradoPorId, "p.registrado_por = :registradoPor", "registradoPor")
                .si(desde, "p.fecha_pago >= :desde", "desde")
                .si(hasta, "p.fecha_pago < :hasta", "hasta");

        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pagos p JOIN clientes c ON c.id = p.cliente_id" + f.where(),
                f.params(), Long.class);

        String sql = """
                SELECT p.id, p.cliente_id, c.nombre_negocio, c.ruta_id, p.monto, p.fecha_pago,
                       p.metodo, p.origen, p.registrado_por, u.nombre AS registrado_por_nombre,
                       p.referencia,
                       COALESCE(ap.notas, 0)    AS notas,
                       COALESCE(ap.aplicado, 0) AS aplicado
                FROM pagos p
                LEFT JOIN (
                    SELECT pago_id, COUNT(*) AS notas, SUM(monto) AS aplicado
                    FROM aplicaciones_pago
                    GROUP BY pago_id
                ) ap ON ap.pago_id = p.id
                JOIN clientes c ON c.id = p.cliente_id
                LEFT JOIN usuarios u ON u.id = p.registrado_por
                """ + f.where() + "\nORDER BY p.fecha_pago DESC, p.id DESC\nLIMIT :tamano OFFSET :offset";
        f.param("tamano", tamano).param("offset", (long) pagina * tamano);

        List<PagoResumenDto> contenido = jdbc.query(sql, f.params(), (rs, i) -> new PagoResumenDto(
                rs.getLong("id"),
                rs.getLong("cliente_id"),
                rs.getString("nombre_negocio"),
                (Long) rs.getObject("ruta_id"),
                rs.getBigDecimal("monto"),
                rs.getObject("fecha_pago", OffsetDateTime.class).toInstant(),
                MetodoPago.valueOf(rs.getString("metodo")),
                OrigenPago.valueOf(rs.getString("origen")),
                (Long) rs.getObject("registrado_por"),
                rs.getString("registrado_por_nombre"),
                rs.getString("referencia"),
                rs.getInt("notas"),
                rs.getBigDecimal("aplicado"),
                rs.getBigDecimal("monto").subtract(rs.getBigDecimal("aplicado"))));

        return Pagina.of(contenido, pagina, tamano, total == null ? 0 : total);
    }

    /** Lo que debe el cliente hoy (notas vivas). */
    public BigDecimal saldoDe(Long clienteId) {
        return jdbc.queryForObject("""
                        SELECT COALESCE(SUM(saldo), 0) FROM notas
                        WHERE cliente_id = :id AND estado <> 'CANCELADA' AND saldo > 0
                        """,
                new MapSqlParameterSource("id", clienteId), BigDecimal.class);
    }
}
