package mx.bronma.cobranza.importacion;

import mx.bronma.cobranza.common.Pagina;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/v1/importaciones")
public class ImportacionController {

    private final ImportacionService service;

    public ImportacionController(ImportacionService service) {
        this.service = service;
    }

    /**
     * Sube el Excel (.xlsx/.xls) o CSV exportado del sistema de ventas.
     * multipart/form-data con el archivo en el campo "archivo".
     *
     * 201 con el resultado. Con simular=true no guarda nada y responde 200:
     * sirve para revisar los errores antes de importar de verdad.
     * 422 si el archivo no se puede leer o le faltan columnas obligatorias.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportacionDto> importar(
            @RequestPart("archivo") MultipartFile archivo,
            @RequestParam(required = false) Long usuarioId,
            @RequestParam(defaultValue = "false") boolean simular) {

        ImportacionDto resultado = service.importar(archivo, usuarioId, simular);
        return ResponseEntity.status(simular ? HttpStatus.OK : HttpStatus.CREATED).body(resultado);
    }

    /** Historial, la mas reciente primero. Sin el detalle de errores. */
    @GetMapping
    public Pagina<ImportacionResumenDto> listar(
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return service.listar(pagina, tamano);
    }

    @GetMapping("/{id}")
    public ImportacionDto obtener(@PathVariable Long id) {
        return service.obtener(id);
    }

    /**
     * Que columnas entiende la importacion y con que nombres. Para que el
     * frontend muestre una guia junto al boton de subir archivo.
     */
    @GetMapping("/columnas")
    public List<ColumnaDto> columnas() {
        return Arrays.stream(ColumnaImportacion.values())
                .map(c -> new ColumnaDto(c.name(), c.isObligatoria(), c.getAliasPrimarios()))
                .toList();
    }

    public record ColumnaDto(String columna, boolean obligatoria, List<String> nombresAceptados) {
    }
}
