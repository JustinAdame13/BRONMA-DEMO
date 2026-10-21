package mx.bronma.cobranza.pago;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mx.bronma.cobranza.cliente.Cliente;
import mx.bronma.cobranza.usuario.Usuario;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Lo que entrega el cliente. Como se reparte entre sus notas vive en
 * {@link AplicacionPago}: un pago puede cubrir varias notas.
 */
@Entity
@Table(name = "pagos")
@Getter
@Setter
@NoArgsConstructor
public class Pago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    /** Cuando se cobro (puede ser antes de que se registre, si el celular no tenia senal). */
    @Column(name = "fecha_pago", nullable = false)
    private Instant fechaPago;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private MetodoPago metodo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private OrigenPago origen;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registrado_por")
    private Usuario registradoPor;

    /** Numero de transferencia, folio del deposito, etc. */
    @Column(length = 100)
    private String referencia;

    @Column(length = 255)
    private String comentario;

    /** Generado por el celular: si reintenta el envio, el UNIQUE evita el pago doble. */
    @Column(name = "uuid_dispositivo", unique = true)
    private UUID uuidDispositivo;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    /** Se guardan junto con el pago (cascade) en la misma transaccion. */
    @OneToMany(mappedBy = "pago", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AplicacionPago> aplicaciones = new ArrayList<>();
}
