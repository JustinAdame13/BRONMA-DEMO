package mx.bronma.cobranza.nota;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mx.bronma.cobranza.cliente.Cliente;
import mx.bronma.cobranza.importacion.Importacion;
import mx.bronma.cobranza.ruta.Ruta;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Una nota de venta a credito: la unidad de la cuenta por cobrar. */
@Entity
@Table(name = "notas")
@Getter
@Setter
@NoArgsConstructor
public class Nota {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Folio en el sistema de ventas: llave de la importacion idempotente. */
    @Column(name = "folio_origen", nullable = false, unique = true, length = 40)
    private String folioOrigen;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    /** Foto de la ruta al momento de la venta (no cambia si el cliente se mueve de ruta). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ruta_id")
    private Ruta ruta;

    /** De que archivo vino; null si se capturo a mano. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "importacion_id")
    private Importacion importacion;

    @Column(name = "fecha_emision", nullable = false)
    private LocalDate fechaEmision;

    @Column(name = "fecha_vencimiento", nullable = false)
    private LocalDate fechaVencimiento;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal saldo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EstadoNota estado = EstadoNota.ABIERTA;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @UpdateTimestamp
    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    /** hoy se recibe como parametro: "hoy" depende de la zona horaria de configuracion. */
    public boolean estaVencida(LocalDate hoy) {
        return estado != EstadoNota.CANCELADA
                && saldo.signum() > 0
                && fechaVencimiento.isBefore(hoy);
    }

    /** Dias de atraso (0 si no esta vencida). Sirve para la antiguedad de cartera. */
    public long diasVencida(LocalDate hoy) {
        return estaVencida(hoy) ? ChronoUnit.DAYS.between(fechaVencimiento, hoy) : 0;
    }
}
