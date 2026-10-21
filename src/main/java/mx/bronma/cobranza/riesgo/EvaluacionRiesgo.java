package mx.bronma.cobranza.riesgo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mx.bronma.cobranza.Enums.Enums;
import mx.bronma.cobranza.cliente.Cliente;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Resultado de Ayax para un cliente en un dia. Una por cliente + dia + modelo:
 * recalcular el mismo dia reemplaza; dias distintos forman el historial
 * (sirve para ver si un cliente va mejorando o empeorando).
 */
@Entity
@Table(name = "evaluaciones_riesgo",
        uniqueConstraints = @UniqueConstraint(name = "uk_evaluaciones_cliente_fecha_modelo",
                columnNames = {"cliente_id", "fecha_calculo", "modelo"}))
@Getter
@Setter
@NoArgsConstructor
public class EvaluacionRiesgo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @Column(name = "fecha_calculo", nullable = false)
    private LocalDate fechaCalculo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 5)
    private Enums.ModeloAyax modelo;

    /** 0 = sin riesgo ... 100 = casi seguro que no paga. */
    @Column(nullable = false)
    private Integer score;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 5)
    private NivelRiesgo nivel;

    // ---- Las senales que usa Ayax (se guardan para poder explicar el score) ----

    @Column(name = "dias_promedio_retraso", precision = 6, scale = 2)
    private BigDecimal diasPromedioRetraso;

    /** Cambio relativo del saldo: 0.25 = la deuda crecio 25 %. */
    @Column(name = "tendencia_deuda", precision = 7, scale = 4)
    private BigDecimal tendenciaDeuda;

    /** saldo / limite: 1.10 = se paso 10 % de su limite. */
    @Column(name = "uso_credito", precision = 7, scale = 4)
    private BigDecimal usoCredito;

    /** Dias promedio entre una nota y la siguiente. */
    @Column(name = "frecuencia_compra_dias", precision = 6, scale = 2)
    private BigDecimal frecuenciaCompraDias;

    @Column(name = "saldo_vencido", nullable = false, precision = 12, scale = 2)
    private BigDecimal saldoVencido = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "accion_recomendada", nullable = false, length = 30)
    private AccionRecomendada accionRecomendada;

    @Column(columnDefinition = "text")
    private String explicacion;

    @CreationTimestamp
    @Column(name = "generada_en", nullable = false, updatable = false)
    private Instant generadaEn;
}
