package mx.bronma.cobranza.pago;

import jakarta.validation.Valid;
import mx.bronma.cobranza.common.Pagina;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/pagos")
public class PagoController {

    private final PagoService service;

    public PagoController(PagoService service) {
        this.service = service;
    }

    /**
     * Registra un pago desde la oficina o desde el celular del repartidor.
     * 201 si es nuevo; 200 si el uuidDispositivo ya existia (reintento del celular).
     * 422 si el monto excede la deuda o las aplicaciones no cuadran.
     */
    @PostMapping
    public ResponseEntity<PagoDto> registrar(@Valid @RequestBody PagoRequest req) {
        PagoService.Registro r = service.registrar(req);
        return ResponseEntity.status(r.nuevo() ? HttpStatus.CREATED : HttpStatus.OK).body(r.pago());
    }

    /**
     * Paginado, el mas reciente primero. desde/hasta (aaaa-mm-dd) son dias en la
     * zona del negocio, ambos inclusive. rutaId es la ruta actual del cliente.
     */
    @GetMapping
    public Pagina<PagoResumenDto> listar(
            @RequestParam(required = false) Long clienteId,
            @RequestParam(required = false) Long rutaId,
            @RequestParam(required = false) MetodoPago metodo,
            @RequestParam(required = false) OrigenPago origen,
            @RequestParam(required = false) Long registradoPorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {

        return service.listar(clienteId, rutaId, metodo, origen, registradoPorId, desde, hasta, pagina, tamano);
    }

    @GetMapping("/{id}")
    public PagoDto obtener(@PathVariable Long id) {
        return service.obtener(id);
    }

    /** Revierte un pago capturado por error: los abonos regresan a sus notas. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revertir(@PathVariable Long id) {
        service.revertir(id);
    }
}
