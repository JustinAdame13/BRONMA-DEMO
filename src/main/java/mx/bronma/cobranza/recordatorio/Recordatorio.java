package mx.bronma.cobranza.recordatorio;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mx.bronma.cobranza.nota.Nota;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDate;

/** Un mensaje de cobranza programado (y despues enviado) para una nota. */
@Entity
@Table(name = "recordatorios",
        uniqueConstraints = @UniqueConstraint(name = "uk_recordatorios_nota_politica", columnNames = {"nota_id", "politica_id"}))
@Getter
@Setter
@NoArgsConstructor
public class Recordatorio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "nota_id", nullable = false)
    private Nota nota;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "politica_id", nullable = false)
    private PoliticaRecordatorio politica;

    /** fecha_vencimiento + dias_offset de la politica. */
    @Column(name = "programado_para", nullable = false)
    private LocalDate programadoPara;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EstadoRecordatorio estado = EstadoRecordatorio.PENDIENTE;

    @Column(nullable = false)
    private Integer intentos = 0;

    /** Foto de lo que se mando (si el cliente cambia de numero, aqui queda a cual se envio). */
    @Column(name = "telefono_destino", length = 16)
    private String telefonoDestino;

    @Column(columnDefinition = "text")
    private String mensaje;

    /** SID de Twilio o id de WhatsApp Business, para rastrear el envio. */
    @Column(name = "mensaje_proveedor_id", length = 64)
    private String mensajeProveedorId;

    @Column(length = 500)
    private String error;

    @Column(name = "enviado_en")
    private Instant enviadoEn;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;
}
