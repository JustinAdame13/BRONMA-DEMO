package mx.bronma.cobranza.nota;

import jakarta.validation.Valid;
import mx.bronma.cobranza.common.Pagina;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/notas")
public class NotaController {

    private final NotaService service;

    public NotaController(NotaService service) {
        this.service = service;
    }

    /**
     * Paginado, la que vence antes primero. Filtros opcionales y combinables.
     * vivas=true: con saldo y no canceladas. vencidas=true: vivas que ya vencieron.
     * desde/hasta: fecha de emision (aaaa-mm-dd). q: busca en el folio.
     */
    @GetMapping
    public Pagina<NotaDto> listar(
            @RequestParam(required = false) Long clienteId,
            @RequestParam(required = false) Long rutaId,
            @RequestParam(required = false) EstadoNota estado,
            @RequestParam(required = false) Boolean vivas,
            @RequestParam(required = false) Boolean vencidas,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {

        return service.listar(clienteId, rutaId, estado, vivas, vencidas, q, desde, hasta, pagina, tamano);
    }

    /** La nota con su historial de abonos. */
    @GetMapping("/{id}")
    public NotaDetalleDto obtener(@PathVariable Long id) {
        return service.obtener(id);
    }

    /** Captura manual de una venta a credito. 409 si el folio ya existe. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NotaDetalleDto crear(@Valid @RequestBody NotaRequest req) {
        return service.crear(req);
    }

    /**
     * No hay DELETE: una nota se cancela. Si ya tenia abonos, ese dinero queda
     * como saldo a favor del cliente.
     */
    @PatchMapping("/{id}/cancelar")
    public NotaDetalleDto cancelar(@PathVariable Long id) {
        return service.cancelar(id);
    }
}
