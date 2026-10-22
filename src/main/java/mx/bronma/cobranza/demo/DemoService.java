package mx.bronma.cobranza.demo;

import mx.bronma.cobranza.cliente.Cliente;
import mx.bronma.cobranza.cliente.ClienteRepository;
import mx.bronma.cobranza.cliente.UbicacionFuente;
import mx.bronma.cobranza.configuracion.Configuracion;
import mx.bronma.cobranza.configuracion.ConfiguracionRepository;
import mx.bronma.cobranza.configuracion.ConfiguracionService;
import mx.bronma.cobranza.nota.EstadoNota;
import mx.bronma.cobranza.nota.Nota;
import mx.bronma.cobranza.nota.NotaRepository;
import mx.bronma.cobranza.pago.MetodoPago;
import mx.bronma.cobranza.pago.OrigenPago;
import mx.bronma.cobranza.pago.PagoRequest;
import mx.bronma.cobranza.pago.PagoService;
import mx.bronma.cobranza.recordatorio.PlantillaMensaje;
import mx.bronma.cobranza.recordatorio.RecordatorioService;
import mx.bronma.cobranza.riesgo.RiesgoService;
import mx.bronma.cobranza.ruta.Ruta;
import mx.bronma.cobranza.ruta.RutaRepository;
import mx.bronma.cobranza.usuario.RolUsuario;
import mx.bronma.cobranza.usuario.Usuario;
import mx.bronma.cobranza.usuario.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * B5: datos de demo. Solo existe con el perfil "demo" (SPRING_PROFILES_ACTIVE=demo):
 * en produccion este bean ni se crea.
 *
 * Todas las fechas se calculan contra el HOY del negocio, asi la demo siempre
 * tiene notas al corriente, por vencer, vencidas en los tres rangos de
 * antiguedad y con pagos parciales, sin importar que dia se cargue.
 *
 * Las tienditas salen de resources/demo/tienditas.csv (nombre, colonia, ruta,
 * coordenadas y perfil de pago). Para mover un pin basta con editar ese archivo.
 *
 * Perfiles de pago:
 * <ul>
 *   <li>PUNTUAL: paga antes o el dia del vencimiento.</li>
 *   <li>REGULAR: paga hasta 6 dias tarde; 4 de cada 10 notas en dos abonos.</li>
 *   <li>TARDIO: paga de 10 a 35 dias tarde (lo reciente queda vencido de 1 a 30 dias).</li>
 *   <li>MOROSO: dejo de comprar hace mas de un mes y dejo de pagar hace dos (vencido de 31 a 90+ dias).</li>
 *   <li>NUEVO: empezo a comprar hace dos semanas; nada vencido.</li>
 * </ul>
 * Los pagos se registran con PagoService, igual que en la vida real.
 */
@Service
@Profile("demo")
public class DemoService {

    private static final Logger log = LoggerFactory.getLogger(DemoService.class);

    private static final int DIAS_HISTORIA = 120;
    private static final BigDecimal LIMITE_DEFAULT_DEMO = new BigDecimal("5000.00");

    private final NamedParameterJdbcTemplate jdbc;
    private final UsuarioRepository usuarios;
    private final RutaRepository rutas;
    private final ClienteRepository clientes;
    private final NotaRepository notas;
    private final PagoService pagos;
    private final RecordatorioService recordatorios;
    private final RiesgoService riesgo;
    private final ConfiguracionService configuracion;
    private final ConfiguracionRepository configuracionRepository;
    private final TransactionTemplate tx;
    private final List<String> telefonosEquipo;

    public DemoService(NamedParameterJdbcTemplate jdbc,
                       UsuarioRepository usuarios,
                       RutaRepository rutas,
                       ClienteRepository clientes,
                       NotaRepository notas,
                       PagoService pagos,
                       RecordatorioService recordatorios,
                       RiesgoService riesgo,
                       ConfiguracionService configuracion,
                       ConfiguracionRepository configuracionRepository,
                       TransactionTemplate tx,
                       @Value("${app.demo.telefonos:}") String telefonosEquipo) {
        this.jdbc = jdbc;
        this.usuarios = usuarios;
        this.rutas = rutas;
        this.clientes = clientes;
        this.notas = notas;
        this.pagos = pagos;
        this.recordatorios = recordatorios;
        this.riesgo = riesgo;
        this.configuracion = configuracion;
        this.configuracionRepository = configuracionRepository;
        this.tx = tx;
        this.telefonosEquipo = Arrays.stream(telefonosEquipo.split(","))
                .map(String::trim)
                .filter(s -> s.matches("^\\+[1-9][0-9]{7,14}$"))
                .toList();
    }

    public record ResumenDemo(LocalDate hoy, int rutas, int clientes, int notas, int pagos,
                              int recordatoriosHistoricos, int evaluados) {
    }

    public boolean hayDatos() {
        return clientes.count() > 0;
    }

    /** Borra todo lo operativo. Configuracion y politica de recordatorios se quedan. */
    public void borrar() {
        tx.executeWithoutResult(st -> jdbc.getJdbcTemplate().execute("""
                TRUNCATE TABLE recordatorios, evaluaciones_riesgo, aplicaciones_pago, pagos, notas,
                               importaciones, clientes, rutas, usuarios
                RESTART IDENTITY CASCADE
                """));
    }

    // ------------------------------------------------------------------
    // Carga
    // ------------------------------------------------------------------

    public ResumenDemo cargar() {
        long t0 = System.currentTimeMillis();
        Configuracion cfg = configuracion.obtenerEntidad();
        LocalDate hoy = cfg.hoy();
        ZoneId zona = cfg.zona();

        // Sin limite general, "uso del credito" no significa nada y Ayax pierde una senal
        tx.executeWithoutResult(st -> {
            Configuracion c = configuracion.obtenerEntidad();
            if (c.getLimiteCreditoDefault() == null || c.getLimiteCreditoDefault().signum() == 0) {
                c.setLimiteCreditoDefault(LIMITE_DEFAULT_DEMO);
                configuracionRepository.save(c);
            }
        });

        // 1. Catalogos y notas, en una transaccion
        Plan plan = tx.execute(st -> crearCatalogosYNotas(hoy, zona));

        // 2. Pagos en orden cronologico, cada uno por PagoService (su propia transaccion)
        plan.pagos.sort(Comparator.comparing(PagoPlan::instante));
        for (PagoPlan p : plan.pagos) {
            pagos.registrar(new PagoRequest(p.clienteId(), p.monto(), p.instante(), p.metodo(), p.origen(),
                    p.registradoPorId(), p.metodo() == MetodoPago.TRANSFERENCIA ? "SPEI demo" : null, null, null,
                    List.of(new PagoRequest.AplicacionRequest(p.notaId(), p.monto()))));
        }

        // 3. Historial de recordatorios ya enviados (para "recuperado por recordatorios")
        Integer historicos = tx.execute(st -> insertarRecordatoriosHistoricos(plan.recordatorios));

        // 4. Programar los pendientes (sin mandar nada) y evaluar con Ayax
        recordatorios.programar();
        int evaluados = riesgo.evaluar(null, false).evaluados();

        log.info("Datos de demo cargados en {} ms", System.currentTimeMillis() - t0);
        return new ResumenDemo(hoy, plan.rutas, plan.clientes, plan.notas, plan.pagos.size(),
                historicos == null ? 0 : historicos, evaluados);
    }

    private Plan crearCatalogosYNotas(LocalDate hoy, ZoneId zona) {
        Plan plan = new Plan();

        // ---------- Usuarios ----------
        usuarios.save(usuario("Oficina Bronma", "+528710000001", RolUsuario.OFICINA));
        usuarios.save(usuario("Administrador", "+528710000002", RolUsuario.ADMIN));
        Usuario rep1 = usuarios.save(usuario("Juan Perez (repartidor)", "+528710000011", RolUsuario.REPARTIDOR));
        Usuario rep2 = usuarios.save(usuario("Luis Hernandez (repartidor)", "+528710000012", RolUsuario.REPARTIDOR));
        Usuario rep3 = usuarios.save(usuario("Carlos Ramirez (repartidor)", "+528710000013", RolUsuario.REPARTIDOR));

        // ---------- Rutas ----------
        Map<String, Ruta> porCodigo = new HashMap<>();
        porCodigo.put("R-CENTRO", rutas.save(ruta("R-CENTRO", "Ruta Centro", "#2563EB", rep1)));
        porCodigo.put("R-ORIENTE", rutas.save(ruta("R-ORIENTE", "Ruta Oriente", "#16A34A", rep2)));
        porCodigo.put("R-SUR", rutas.save(ruta("R-SUR", "Ruta Sur", "#DC2626", rep3)));
        plan.rutas = porCodigo.size();

        // ---------- Clientes y notas ----------
        Map<String, Integer> ordenPorRuta = new HashMap<>();
        int folio = 1;
        List<FilaDemo> filas = leerCsv();

        for (int i = 0; i < filas.size(); i++) {
            FilaDemo f = filas.get(i);
            Random r = new Random(1000L + i); // determinista: misma demo cada vez
            Ruta ruta = porCodigo.get(f.ruta());

            Cliente c = new Cliente();
            c.setCodigoOrigen(f.codigo());
            c.setNombreNegocio(f.nombre());
            c.setContacto(f.contacto());
            c.setDireccion(f.direccion());
            c.setRuta(ruta);
            c.setPlazoDias(i % 3 == 0 ? 15 : 7);
            c.setLimiteCredito(BigDecimal.valueOf(3000 + r.nextInt(6) * 1000L).setScale(2, RoundingMode.HALF_UP));

            // Telefonos ficticios, salvo los del equipo (DEMO_TELEFONOS) para probar WhatsApp de verdad
            if (i < telefonosEquipo.size()) {
                c.setTelefonoWhatsapp(telefonosEquipo.get(i));
                c.setAceptaWhatsapp(true);
            } else {
                c.setTelefonoWhatsapp(String.format("+52871555%04d", 1000 + i));
                c.setAceptaWhatsapp(i % 10 < 7);
            }

            if (f.latitud() != null) {
                c.setLatitud(f.latitud());
                c.setLongitud(f.longitud());
                c.setUbicacionFuente(UbicacionFuente.MANUAL);
                c.setUbicacionFijadaEn(Instant.now());
                c.setOrdenVisita(ordenPorRuta.merge(f.ruta(), 1, Integer::sum));
            }
            clientes.save(c);
            plan.clientes++;

            folio = crearNotas(c, ruta, f.perfil(), r, hoy, zona, folio, plan);
        }
        return plan;
    }

    /** Las notas de un cliente segun su perfil, y los pagos y recordatorios que les tocan. */
    private int crearNotas(Cliente c, Ruta ruta, String perfil, Random r, LocalDate hoy, ZoneId zona,
                           int folio, Plan plan) {

        int plazo = c.getPlazoDias();
        int frecuencia = 3 + r.nextInt(6);

        LocalDate inicio = perfil.equals("NUEVO") ? hoy.minusDays(12) : hoy.minusDays(DIAS_HISTORIA);
        LocalDate fin = perfil.equals("MOROSO") ? hoy.minusDays(35) : hoy.minusDays(1);

        int indice = 0;
        for (LocalDate d = inicio; !d.isAfter(fin); d = d.plusDays(frecuencia), indice++) {
            BigDecimal monto = BigDecimal.valueOf((30 + r.nextInt(200)) * 10L).setScale(2, RoundingMode.HALF_UP);
            LocalDate vence = d.plusDays(plazo);

            Nota n = new Nota();
            n.setFolioOrigen(String.format("DEMO-%05d", folio++));
            n.setCliente(c);
            n.setRuta(ruta);
            n.setFechaEmision(d);
            n.setFechaVencimiento(vence);
            n.setMonto(monto);
            n.setSaldo(monto);
            n.setEstado(EstadoNota.ABIERTA);
            notas.save(n);
            plan.notas++;

            switch (perfil) {
                case "PUNTUAL" -> pagar(plan, c, ruta, n, monto, vence.minusDays(r.nextInt(3)), hoy, zona, r);
                case "REGULAR" -> {
                    LocalDate dia = vence.plusDays(r.nextInt(7));
                    if (r.nextInt(10) < 4) {
                        BigDecimal mitad = monto.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
                        pagar(plan, c, ruta, n, mitad, dia, hoy, zona, r);
                        pagar(plan, c, ruta, n, monto.subtract(mitad), dia.plusDays(5 + r.nextInt(5)), hoy, zona, r);
                    } else {
                        pagar(plan, c, ruta, n, monto, dia, hoy, zona, r);
                    }
                }
                case "TARDIO" -> {
                    LocalDate dia = vence.plusDays(10 + r.nextInt(25));
                    if (r.nextInt(10) < 3) {
                        BigDecimal abono = monto.multiply(new BigDecimal("0.40")).setScale(2, RoundingMode.HALF_UP);
                        pagar(plan, c, ruta, n, abono, vence.plusDays(5), hoy, zona, r);
                        pagar(plan, c, ruta, n, monto.subtract(abono), dia, hoy, zona, r);
                    } else {
                        pagar(plan, c, ruta, n, monto, dia, hoy, zona, r);
                    }
                }
                case "MOROSO" -> {
                    if (d.isBefore(hoy.minusDays(90))) {
                        pagar(plan, c, ruta, n, monto, vence.plusDays(15 + r.nextInt(20)), hoy.minusDays(60), zona, r);
                    } else if (indice % 2 == 0) {
                        // Un abono chiquito de vez en cuando: el clasico "ahorita le doy algo"
                        BigDecimal abono = monto.multiply(new BigDecimal("0.25")).setScale(2, RoundingMode.HALF_UP);
                        pagar(plan, c, ruta, n, abono, vence.plusDays(20), hoy, zona, r);
                    }
                }
                default -> {
                    // NUEVO: no ha pagado nada, todo al corriente o por vencer
                }
            }

            // Recordatorio +7 enviado a las notas que siguen vencidas (para que el historial se vea real)
            if (Boolean.TRUE.equals(c.getAceptaWhatsapp()) && !vence.plusDays(7).isAfter(hoy)
                    && (perfil.equals("TARDIO") || perfil.equals("MOROSO"))) {
                plan.recordatorios.add(new RecordatorioPlan(n.getId(), 7, vence.plusDays(7), c.getTelefonoWhatsapp(),
                        c.getNombreNegocio(), n.getFolioOrigen(), monto, vence));
            }
        }
        return folio;
    }

    /**
     * Programa un pago si su dia ya paso (lo del futuro queda como saldo). Si se
     * pago tarde y el cliente acepta WhatsApp, deja tambien el recordatorio +3
     * que "lo convencio" (enviado hasta 7 dias antes del pago).
     */
    private void pagar(Plan plan, Cliente c, Ruta ruta, Nota n, BigDecimal monto, LocalDate dia, LocalDate tope,
                       ZoneId zona, Random r) {
        if (dia.isAfter(tope)) {
            return;
        }
        Instant instante = dia.atTime(LocalTime.of(9 + r.nextInt(8), r.nextInt(60))).atZone(zona).toInstant();
        Instant ahora = Instant.now();
        if (instante.isAfter(ahora)) {
            instante = ahora.minus(5, ChronoUnit.MINUTES);
        }

        boolean transferencia = r.nextInt(10) < 3;
        plan.pagos.add(new PagoPlan(instante, c.getId(), n.getId(), monto,
                transferencia ? MetodoPago.TRANSFERENCIA : MetodoPago.EFECTIVO,
                transferencia ? OrigenPago.OFICINA : OrigenPago.RUTA,
                transferencia ? null : ruta.getRepartidor().getId()));

        LocalDate recordatorio3 = n.getFechaVencimiento().plusDays(3);
        long diasAntes = ChronoUnit.DAYS.between(recordatorio3, dia);
        if (Boolean.TRUE.equals(c.getAceptaWhatsapp()) && diasAntes >= 0 && diasAntes <= 7) {
            plan.recordatorios.add(new RecordatorioPlan(n.getId(), 3, recordatorio3, c.getTelefonoWhatsapp(),
                    c.getNombreNegocio(), n.getFolioOrigen(), n.getMonto(), n.getFechaVencimiento()));
        }
    }

    private int insertarRecordatoriosHistoricos(List<RecordatorioPlan> lista) {
        Map<Integer, Long> politicaPorOffset = new HashMap<>();
        Map<Integer, String> plantillaPorOffset = new HashMap<>();
        jdbc.query("SELECT id, dias_offset, plantilla FROM politica_recordatorios",
                (rs, i) -> {
                    politicaPorOffset.put(rs.getInt("dias_offset"), rs.getLong("id"));
                    plantillaPorOffset.put(rs.getInt("dias_offset"), rs.getString("plantilla"));
                    return null;
                });

        ZoneId zona = configuracion.obtenerEntidad().zona();
        int insertados = 0;
        for (RecordatorioPlan p : lista) {
            Long politicaId = politicaPorOffset.get(p.diasOffset());
            if (politicaId == null) {
                continue; // alguien borro o cambio esa regla
            }
            String mensaje = PlantillaMensaje.llenar(plantillaPorOffset.get(p.diasOffset()), p.cliente(),
                    p.folio(), p.saldo(), p.vencimiento());
            insertados += jdbc.update("""
                    INSERT INTO recordatorios (nota_id, politica_id, programado_para, estado, intentos,
                                               telefono_destino, mensaje, mensaje_proveedor_id, enviado_en)
                    VALUES (:nota, :politica, :programado, 'ENVIADO', 1, :telefono, :mensaje, :proveedorId, :enviado)
                    ON CONFLICT (nota_id, politica_id) DO NOTHING
                    """, new MapSqlParameterSource()
                    .addValue("nota", p.notaId())
                    .addValue("politica", politicaId)
                    .addValue("programado", p.programado())
                    .addValue("telefono", p.telefono())
                    .addValue("mensaje", mensaje)
                    .addValue("proveedorId", "demo-" + p.notaId() + "-" + p.diasOffset())
                    .addValue("enviado", p.programado().atTime(9, 0).atZone(zona).toOffsetDateTime()));
        }
        return insertados;
    }

    // ------------------------------------------------------------------
    // Apoyos
    // ------------------------------------------------------------------

    private static Usuario usuario(String nombre, String telefono, RolUsuario rol) {
        Usuario u = new Usuario();
        u.setNombre(nombre);
        u.setTelefono(telefono);
        u.setRol(rol);
        u.setActivo(true);
        return u;
    }

    private static Ruta ruta(String codigo, String nombre, String color, Usuario repartidor) {
        Ruta r = new Ruta();
        r.setCodigoOrigen(codigo);
        r.setNombre(nombre);
        r.setColor(color);
        r.setRepartidor(repartidor);
        r.setActivo(true);
        return r;
    }

    private record FilaDemo(String codigo, String nombre, String contacto, String colonia, String ruta,
                            Double latitud, Double longitud, String perfil, String direccion) {
    }

    /** CSV propio y controlado: comas como separador y solo la direccion entre comillas. */
    private static List<FilaDemo> leerCsv() {
        List<FilaDemo> filas = new ArrayList<>();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(
                new ClassPathResource("demo/tienditas.csv").getInputStream(), StandardCharsets.UTF_8))) {
            String linea = in.readLine(); // encabezado
            while ((linea = in.readLine()) != null) {
                if (linea.isBlank()) {
                    continue;
                }
                List<String> c = separar(linea);
                filas.add(new FilaDemo(c.get(0), c.get(1), c.get(2), c.get(3), c.get(4),
                        c.get(5).isBlank() ? null : Double.valueOf(c.get(5)),
                        c.get(6).isBlank() ? null : Double.valueOf(c.get(6)),
                        c.get(7), c.get(8)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer demo/tienditas.csv", e);
        }
        return filas;
    }

    private static List<String> separar(String linea) {
        List<String> campos = new ArrayList<>();
        StringBuilder actual = new StringBuilder();
        boolean comillas = false;
        for (char ch : linea.toCharArray()) {
            if (ch == '"') {
                comillas = !comillas;
            } else if (ch == ',' && !comillas) {
                campos.add(actual.toString().trim());
                actual.setLength(0);
            } else {
                actual.append(ch);
            }
        }
        campos.add(actual.toString().trim());
        return campos;
    }

    private record PagoPlan(Instant instante, Long clienteId, Long notaId, BigDecimal monto,
                            MetodoPago metodo, OrigenPago origen, Long registradoPorId) {
    }

    private record RecordatorioPlan(Long notaId, int diasOffset, LocalDate programado, String telefono,
                                    String cliente, String folio, BigDecimal saldo, LocalDate vencimiento) {
    }

    private static final class Plan {
        int rutas;
        int clientes;
        int notas;
        final List<PagoPlan> pagos = new ArrayList<>();
        final List<RecordatorioPlan> recordatorios = new ArrayList<>();
    }
}
