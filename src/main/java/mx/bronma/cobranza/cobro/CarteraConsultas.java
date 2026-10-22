package mx.bronma.cobranza.cobro;

import mx.bronma.cobranza.cliente.EstadoCredito;
import mx.bronma.cobranza.common.FiltroSql;
import mx.bronma.cobranza.riesgo.AccionRecomendada;
import mx.bronma.cobranza.riesgo.NivelRiesgo;
import mx.bronma.cobranza.riesgo.RiesgoConsultas;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Saldos por cliente + evaluacion vigente de Ayax, en UNA consulta (sin N+1).
 * La comparten la lista de cobro, el mapa y el dashboard.
 */
@Repository
public class CarteraConsultas {

    private final NamedParameterJdbcTemplate jdbc;

    public CarteraConsultas(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Clientes activos con sus saldos.
     *
     * @param rutaId        null = todas las rutas.
     * @param repartidorId  null = cualquiera; si viene, solo las rutas activas de ese repartidor.
     * @param diasPorVencer ventana de "vence pronto" (exclusivo hoy, inclusivo hoy + dias).
     */
    public List<CarteraCliente> clientes(Long rutaId, Long repartidorId, LocalDate hoy, int diasPorVencer) {

        FiltroSql f = new FiltroSql()
                .siempre("c.activo")
                .si(rutaId, "c.ruta_id = :rutaId", "rutaId")
                .si(repartidorId, "r.repartidor_id = :repartidorId AND r.activo", "repartidorId");
        f.param("hoy", hoy).param("dias", diasPorVencer);

        String sql = """
                SELECT c.id, c.codigo_origen, c.nombre_negocio, c.contacto, c.telefono_whatsapp, c.direccion,
                       c.ruta_id, r.nombre AS ruta_nombre, r.color AS ruta_color,
                       c.latitud, c.longitud, c.orden_visita, c.estado_credito,
                       COALESCE(s.saldo, 0)          AS saldo,
                       COALESCE(s.saldo_vencido, 0)  AS saldo_vencido,
                       COALESCE(s.exigible, 0)       AS exigible,
                       COALESCE(s.por_vencer, 0)     AS por_vencer,
                       COALESCE(s.dias_max, 0)       AS dias_max,
                       COALESCE(s.notas_vencidas, 0) AS notas_vencidas,
                       ev.score, ev.nivel, ev.accion_recomendada
                FROM clientes c
                LEFT JOIN rutas r ON r.id = c.ruta_id
                LEFT JOIN (
                    SELECT cliente_id,
                           SUM(saldo) AS saldo,
                           SUM(saldo) FILTER (WHERE fecha_vencimiento <  CAST(:hoy AS date)) AS saldo_vencido,
                           SUM(saldo) FILTER (WHERE fecha_vencimiento <= CAST(:hoy AS date)) AS exigible,
                           SUM(saldo) FILTER (WHERE fecha_vencimiento >  CAST(:hoy AS date)
                                              AND fecha_vencimiento <= CAST(:hoy AS date) + CAST(:dias AS integer)) AS por_vencer,
                           MAX(CAST(:hoy AS date) - fecha_vencimiento)
                               FILTER (WHERE fecha_vencimiento < CAST(:hoy AS date)) AS dias_max,
                           COUNT(*) FILTER (WHERE fecha_vencimiento < CAST(:hoy AS date)) AS notas_vencidas
                    FROM notas
                    WHERE estado <> 'CANCELADA' AND saldo > 0
                    GROUP BY cliente_id
                ) s ON s.cliente_id = c.id
                """ + RiesgoConsultas.LATERAL_ULTIMA_EVALUACION + f.where()
                + "\nORDER BY c.ruta_id NULLS LAST, c.orden_visita NULLS LAST, c.nombre_negocio";

        return jdbc.query(sql, f.params(), (rs, i) -> {
            String nivel = rs.getString("nivel");
            String accion = rs.getString("accion_recomendada");
            return new CarteraCliente(
                    rs.getLong("id"),
                    rs.getString("codigo_origen"),
                    rs.getString("nombre_negocio"),
                    rs.getString("contacto"),
                    rs.getString("telefono_whatsapp"),
                    rs.getString("direccion"),
                    (Long) rs.getObject("ruta_id"),
                    rs.getString("ruta_nombre"),
                    rs.getString("ruta_color"),
                    (Double) rs.getObject("latitud"),
                    (Double) rs.getObject("longitud"),
                    (Integer) rs.getObject("orden_visita"),
                    EstadoCredito.valueOf(rs.getString("estado_credito")),
                    rs.getBigDecimal("saldo"),
                    rs.getBigDecimal("saldo_vencido"),
                    rs.getBigDecimal("exigible"),
                    rs.getBigDecimal("por_vencer"),
                    rs.getInt("dias_max"),
                    rs.getInt("notas_vencidas"),
                    (Integer) rs.getObject("score"),
                    nivel == null ? null : NivelRiesgo.valueOf(nivel),
                    accion == null ? null : AccionRecomendada.valueOf(accion));
        });
    }
}
