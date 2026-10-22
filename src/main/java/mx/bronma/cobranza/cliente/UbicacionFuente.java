package mx.bronma.cobranza.cliente;

/** De donde salio la coordenada de un cliente. */
public enum UbicacionFuente {

    /** La oficina hizo clic sobre el mapa: es una aproximacion. */
    MANUAL,

    /** El celular del repartidor, parado afuera de la tienda: es precisa. */
    GPS,

    /** Reservada: deducida de la direccion escrita. El front no la manda. */
    GEOCODIFICADA
}
