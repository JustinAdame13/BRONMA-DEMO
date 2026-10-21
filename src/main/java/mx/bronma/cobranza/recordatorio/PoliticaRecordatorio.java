package mx.bronma.cobranza.recordatorio;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Una regla de "cuando avisar": dias relativos al vencimiento.
 * -3 = tres dias antes, 0 = el dia que vence, 7 = una semana tarde.
 * No se borran (los recordatorios enviados las referencian): se desactivan.
 */
@Entity
@Table(name = "politica_recordatorios")
@Getter
@Setter
@NoArgsConstructor
public class PoliticaRecordatorio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dias_offset", nullable = false, unique = true)
    private Integer diasOffset;

    /** Texto con {cliente}, {folio}, {saldo} y {vencimiento}. */
    @Column(nullable = false, columnDefinition = "text")
    private String plantilla;

    /** Nombre/ContentSid de la plantilla aprobada (WhatsApp Business exige plantillas para avisos). */
    @Column(name = "nombre_plantilla_whatsapp", length = 100)
    private String nombrePlantillaWhatsapp;

    @Column(nullable = false)
    private Boolean activo = true;
}
