package mx.bronma.cobranza.pago;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mx.bronma.cobranza.nota.Nota;

import java.math.BigDecimal;

/**
 * Cuanto de un pago se abono a una nota. Es la tabla intermedia de la
 * relacion muchos a muchos Pago - Nota, con el monto como dato propio.
 */
@Entity
@Table(name = "aplicaciones_pago",
        uniqueConstraints = @UniqueConstraint(name = "uk_aplicaciones_pago_nota", columnNames = {"pago_id", "nota_id"}))
@Getter
@Setter
@NoArgsConstructor
public class AplicacionPago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pago_id", nullable = false)
    private Pago pago;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "nota_id", nullable = false)
    private Nota nota;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;
}
