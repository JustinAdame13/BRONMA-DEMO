package mx.bronma.cobranza.importacion;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mx.bronma.cobranza.usuario.Usuario;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/** Bitacora de cada archivo de ventas que se sube. */
@Entity
@Table(name = "importaciones")
@Getter
@Setter
@NoArgsConstructor
public class Importacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "archivo_nombre", nullable = false, length = 255)
    private String archivoNombre;

    @CreationTimestamp
    @Column(name = "importado_en", nullable = false, updatable = false)
    private Instant importadoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(name = "filas_total", nullable = false)
    private Integer filasTotal = 0;

    @Column(nullable = false)
    private Integer creadas = 0;

    @Column(nullable = false)
    private Integer actualizadas = 0;

    @Column(nullable = false)
    private Integer errores = 0;

    /**
     * JSON con el detalle: [{"fila": 12, "folio": "A-123", "error": "..."}].
     * Se guarda como texto JSON; el servicio lo arma con Jackson.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detalle_errores", columnDefinition = "jsonb")
    private String detalleErrores;
}
