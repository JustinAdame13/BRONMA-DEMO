package mx.bronma.cobranza.cobro;

import mx.bronma.cobranza.common.ReglaNegocioException;
import mx.bronma.cobranza.configuracion.ConfiguracionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class ListaCobroService {

    private final CarteraConsultas cartera;
    private final ConfiguracionService configuracion;

    public ListaCobroService(CarteraConsultas cartera, ConfiguracionService configuracion) {
        this.cartera = cartera;
        this.configuracion = configuracion;
    }

    /**
     * Clientes de la ruta (o de las rutas del repartidor) que tienen algo que
     * cobrar hoy o que vence en los proximos diasPorVencer dias.
     */
    @Transactional(readOnly = true)
    public ListaCobroDto generar(Long rutaId, Long repartidorId, OrdenLista orden, int diasPorVencer) {

        if (rutaId == null && repartidorId == null) {
            throw new ReglaNegocioException("Indica rutaId o repartidorId");
        }
        if (diasPorVencer < 0 || diasPorVencer > 30) {
            throw new ReglaNegocioException("diasPorVencer debe estar entre 0 y 30");
        }
        OrdenLista o = orden == null ? OrdenLista.PRIORIDAD : orden;
        LocalDate hoy = configuracion.obtenerEntidad().hoy();

        List<Fila> filas = new ArrayList<>();
        for (CarteraCliente c : cartera.clientes(rutaId, repartidorId, hoy, diasPorVencer)) {
            if (c.exigible().signum() > 0 || c.porVencer().signum() > 0) {
                filas.add(new Fila(c, c.prioridad()));
            }
        }

        Comparator<Fila> porPrioridad = Comparator
                .comparingInt((Fila f) -> f.p().puntos()).reversed()
                .thenComparing(f -> f.c().diasAtrasoMax(), Comparator.reverseOrder())
                .thenComparing(f -> f.c().nombreNegocio());

        Comparator<Fila> comparador = o == OrdenLista.VISITA
                ? Comparator.comparing((Fila f) -> f.c().rutaId(), Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(f -> f.c().ordenVisita(), Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(porPrioridad)
                : porPrioridad;
        filas.sort(comparador);

        List<ListaCobroDto.Item> items = new ArrayList<>();
        BigDecimal totalACobrar = BigDecimal.ZERO;
        BigDecimal totalPorVencer = BigDecimal.ZERO;
        int posicion = 1;
        for (Fila f : filas) {
            CarteraCliente c = f.c();
            items.add(new ListaCobroDto.Item(
                    posicion++,
                    c.clienteId(),
                    c.codigoOrigen(),
                    c.nombreNegocio(),
                    c.contacto(),
                    c.telefonoWhatsapp(),
                    c.direccion(),
                    c.rutaId(),
                    c.rutaNombre(),
                    c.latitud(),
                    c.longitud(),
                    c.ordenVisita(),
                    c.estadoCredito(),
                    c.saldo(),
                    c.saldoVencido(),
                    c.exigible(),
                    c.porVencer(),
                    c.diasAtrasoMax(),
                    c.notasVencidas(),
                    c.nivelRiesgo(),
                    c.accionRecomendada(),
                    f.p().puntos(),
                    f.p().motivo()));
            totalACobrar = totalACobrar.add(c.exigible());
            totalPorVencer = totalPorVencer.add(c.porVencer());
        }

        return new ListaCobroDto(hoy, rutaId, repartidorId, o, items.size(), totalACobrar, totalPorVencer, items);
    }

    private record Fila(CarteraCliente c, PrioridadCobro.Prioridad p) {
    }
}
