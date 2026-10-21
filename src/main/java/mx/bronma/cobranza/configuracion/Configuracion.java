package mx.bronma.cobranza.configuracion;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mx.bronma.cobranza.Enums.Enums;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/** Una sola fila (id = 1) creada por la migracion V2. */
@Entity
@Table(name = "configuracion")
@Getter
@Setter
@NoArgsConstructor
public class Configuracion {

    public static final short ID_UNICO = 1;

    @Id
    private Short id; // SMALLINT en la BD -> Short

    @Column(name = "nombre_empresa", nullable = false, length = 120)
    private String nombreEmpresa;

    /** El servidor corre en UTC; "hoy" y los vencimientos se calculan en esta zona. */
    @Column(name = "zona_horaria", nullable = false, length = 40)
    private String zonaHoraria;

    /** Plazo para los clientes que no tienen uno propio (clientes.plazo_dias = null). */
    @Column(name = "plazo_credito_default_dias", nullable = false)
    private Integer plazoCreditoDefaultDias;

    /** Limite para los clientes con clientes.limite_credito = 0. */
    @Column(name = "limite_credito_default", nullable = false, precision = 12, scale = 2)
    private BigDecimal limiteCreditoDefault;

    /** Interruptor general: en false el scheduler programa pero no envia. */
    @Column(name = "whatsapp_habilitado", nullable = false)
    private Boolean whatsappHabilitado;

    /** A que hora (en zona_horaria) salen los recordatorios del dia. */
    @Column(name = "hora_envio_recordatorios", nullable = false)
    private LocalTime horaEnvioRecordatorios;

    @Enumerated(EnumType.STRING)
    @Column(name = "proveedor_ia", nullable = false, length = 20)
    private Enums.ProveedorIA proveedorIa = Enums.ProveedorIA.NINGUNO;

    @Column(name = "modelo_ia", length = 80)
    private String modeloIa;

    /** Conveniencia para el frontend y para los if del servicio. */
    @Transient
    public boolean isIaActiva() {
        return proveedorIa != null
                && proveedorIa != Enums.ProveedorIA.NINGUNO;
    }

    @Transient
    public ZoneId zona() {
        return ZoneId.of(zonaHoraria);
    }

    /** "Hoy" del negocio, no del servidor. */
    @Transient
    public LocalDate hoy() {
        return LocalDate.now(zona());
    }
}
