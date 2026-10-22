package mx.bronma.cobranza.ruta;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Cuerpo del PATCH /rutas/{id}/orden.
 * El primero de la lista queda con ordenVisita = 1, el segundo con 2, etc.
 * Los clientes de la ruta que no vengan en la lista quedan en null.
 */
public record OrdenRequest(@NotEmpty List<Long> clienteIds) {
}
