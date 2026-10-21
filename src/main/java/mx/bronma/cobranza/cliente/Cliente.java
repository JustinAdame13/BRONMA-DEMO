package mx.bronma.cobranza.cliente;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mx.bronma.cobranza.ruta.Ruta;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/** Una tiendita o abarrote que compra a credito. */
@Entity
@Table(name = "clientes")
@Getter
@Setter
@NoArgsConstructor
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Clave del cliente en el sistema de ventas: llave de la importacion idempotente. */
    @Column(name = "codigo_origen", nullable = false, unique = true, length = 30)
    private String codigoOrigen;

    @Column(name = "nombre_negocio", nullable = false, length = 150)
    private String nombreNegocio;

    @Column(length = 120)
    private String contacto;

    /** Formato E.164 (+528711234567); la tabla tiene un CHECK con ese formato. */
    @Column(name = "telefono_whatsapp", length = 16)
    private String telefonoWhatsapp;

    @Column(length = 255)
    private String direccion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ruta_id")
    private Ruta ruta;

    /** 0 = usar configuracion.limite_credito_default. */
    @Column(name = "limite_credito", nullable = false, precision = 12, scale = 2)
    private BigDecimal limiteCredito = BigDecimal.ZERO;

    /** null = usar configuracion.plazo_credito_default_dias. */
    @Column(name = "plazo_dias")
    private Integer plazoDias;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_credito", nullable = false, length = 12)
    private EstadoCredito estadoCredito = EstadoCredito.ACTIVO;

    /** Consentimiento para recibir WhatsApp (lo exige WhatsApp Business). */
    @Column(name = "acepta_whatsapp", nullable = false)
    private Boolean aceptaWhatsapp = false;

    @Column(nullable = false)
    private Boolean activo = true;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @UpdateTimestamp
    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;
}
