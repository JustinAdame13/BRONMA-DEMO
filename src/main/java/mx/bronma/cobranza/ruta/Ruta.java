package mx.bronma.cobranza.ruta;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mx.bronma.cobranza.usuario.Usuario;

@Entity
@Table(name = "rutas")
@Getter
@Setter
@NoArgsConstructor
public class Ruta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Clave de la ruta en el sistema de ventas; la importacion busca por aqui. */
    @Column(name = "codigo_origen", nullable = false, unique = true, length = 30)
    private String codigoOrigen;

    @Column(nullable = false, length = 100)
    private String nombre;

    /** Quien recorre la ruta. Puede quedar sin asignar. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repartidor_id")
    private Usuario repartidor;

    @Column(nullable = false)
    private Boolean activo = true;
}
