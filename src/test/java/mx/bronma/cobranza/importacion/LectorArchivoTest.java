package mx.bronma.cobranza.importacion;

import mx.bronma.cobranza.common.ReglaNegocioException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LectorArchivoTest {

    private final LectorArchivo lector = new LectorArchivo();

    private static MockMultipartFile archivo(String nombre, byte[] contenido) {
        return new MockMultipartFile("archivo", nombre, "text/csv", contenido);
    }

    @Test
    void csvConTituloBomPuntoYComaYComillas() {
        String csv = "﻿Reporte de ventas;;\r\n;;\r\n"
                + "No. de Nota;Fecha;Cliente;Nombre del cliente;Total\r\n"
                + "A-1;21/10/2026;C001;\"Abarrotes \"\"La Peña\"\"; sucursal\";$1,250.00\r\n"
                + "\r\n"
                + "A-2;22/10/2026;C002;Tienda Lupita;300\r\n";

        List<FilaArchivo> filas = lector.leer(archivo("ventas.csv", csv.getBytes(StandardCharsets.UTF_8)));

        assertEquals(2, filas.size());
        FilaArchivo primera = filas.get(0);
        assertEquals(4, primera.numero()); // fila como la ve la oficina
        assertEquals("A-1", primera.valor(ColumnaImportacion.FOLIO));
        assertEquals("C001", primera.valor(ColumnaImportacion.CLIENTE_CODIGO));
        assertEquals("Abarrotes \"La Peña\"; sucursal", primera.valor(ColumnaImportacion.CLIENTE_NOMBRE));
        assertEquals("$1,250.00", primera.valor(ColumnaImportacion.MONTO));
        assertEquals(6, filas.get(1).numero());
    }

    @Test
    void csvDeExcelEnWindows1252() {
        String csv = "folio,fecha,cliente,monto\nX1,2026-10-01,Peña,10";
        List<FilaArchivo> filas = lector.leer(archivo("v.csv", csv.getBytes(Charset.forName("windows-1252"))));
        assertEquals("Peña", filas.get(0).valor(ColumnaImportacion.CLIENTE_CODIGO));
    }

    @Test
    void clienteAmbiguoSeVuelveNombreSiYaHayClave() {
        String csv = "Folio\tFecha\tClave\tCliente\tImporte\n9\t1/1/2026\tK1\tDon Pepe\t5\n";
        FilaArchivo f = lector.leer(archivo("v.txt", csv.getBytes(StandardCharsets.UTF_8))).get(0);
        assertEquals("K1", f.valor(ColumnaImportacion.CLIENTE_CODIGO));
        assertEquals("Don Pepe", f.valor(ColumnaImportacion.CLIENTE_NOMBRE));
    }

    @Test
    void faltanColumnasObligatorias() {
        ReglaNegocioException e = assertThrows(ReglaNegocioException.class,
                () -> lector.leer(archivo("v.csv", "folio,fecha,importe\n1,2,3\n".getBytes(StandardCharsets.UTF_8))));
        assertEquals(true, e.getCampos().containsKey("CLIENTE_CODIGO"));
    }

    @Test
    void archivoVacio() {
        assertThrows(ReglaNegocioException.class, () -> lector.leer(archivo("v.csv", new byte[0])));
    }
}
