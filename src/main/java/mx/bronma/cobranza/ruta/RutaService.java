package mx.bronma.cobranza.ruta;

import mx.bronma.cobranza.cliente.Cliente;
import mx.bronma.cobranza.cliente.ClienteRepository;
import mx.bronma.cobranza.cliente.ConteoPorRuta;

import mx.bronma.cobranza.common.ConflictoException;
import mx.bronma.cobranza.common.RecursoNoEncontradoException;
import mx.bronma.cobranza.common.ReglaNegocioException;
import mx.bronma.cobranza.usuario.RolUsuario;
import mx.bronma.cobranza.usuario.Usuario;
import mx.bronma.cobranza.usuario.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RutaService {

    private final RutaRepository rRepository;
    private final ClienteRepository cRepository;
    private final UsuarioRepository uRepository;


    public RutaService(RutaRepository rRepository, ClienteRepository cRepository, UsuarioRepository uRepository){
        this.rRepository=rRepository;
        this.cRepository=cRepository;
        this.uRepository=uRepository;
    }


    @Transactional(readOnly = true)
    public List<RutaDto> listar(Boolean activo){

        List<Ruta> rutas=(activo == null)? rRepository.listarConRepartidor():rRepository.listarConRepartidor(activo);

        List<ConteoPorRuta> conteos = cRepository.contarClientesPorRuta();

        Map<Long, Long> porRuta = conteos.stream()
                .collect(Collectors.toMap(ConteoPorRuta::getRutaId, ConteoPorRuta::getTotal));

        return rutas.stream()
                .map(r -> RutaDto.from(r, porRuta.getOrDefault(r.getId(), 0L)))
                .toList();
    }

    @Transactional
    public RutaDto crear(RutaRequest req) {

        if (rRepository.findByCodigoOrigen(req.codigoOrigen()).isPresent()) {
            throw new ConflictoException("Ya existe una ruta con el codigo " + req.codigoOrigen());
        }

        Ruta r = new Ruta();
        r.setCodigoOrigen(req.codigoOrigen());
        r.setNombre(req.nombre());
        r.setColor(req.color());
        r.setRepartidor(buscarRepartidor(req.repartidorId()));
        r.setActivo(req.activo() == null ? true : req.activo());

        return RutaDto.from(rRepository.save(r), 0L);
    }

    @Transactional
    public RutaDto actualizar(Long id, RutaRequest req) {

        Ruta r = rRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la ruta " + id));

        // Una ruta no choca consigo misma: solo es duplicado si el codigo lo tiene OTRA.
        Optional<Ruta> conMismoCodigo = rRepository.findByCodigoOrigen(req.codigoOrigen());
        if (conMismoCodigo.isPresent() && !conMismoCodigo.get().getId().equals(id)) {
            throw new ConflictoException("Ya existe una ruta con el codigo " + req.codigoOrigen());
        }

        r.setCodigoOrigen(req.codigoOrigen());
        r.setNombre(req.nombre());
        r.setColor(req.color());
        r.setRepartidor(buscarRepartidor(req.repartidorId()));
        r.setActivo(req.activo() == null ? r.getActivo() : req.activo());

        return RutaDto.from(rRepository.save(r), cRepository.countByRutaId(id));
    }

    /**
     * Renumera el orden de visita de una ruta.
     * Un id que no sea de esta ruta corta la operacion completa con 422.
     */
    @Transactional
    public void reordenar(Long rutaId, OrdenRequest req) {

        rRepository.findById(rutaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la ruta " + rutaId));

        List<Cliente> deLaRuta = cRepository.findByRutaIdAndActivoTrue(rutaId);

        Map<Long, Cliente> porId = new HashMap<>();
        for (Cliente c : deLaRuta) {
            porId.put(c.getId(), c);
        }

        int orden = 1;
        Set<Long> mencionados = new HashSet<>();
        for (Long id : req.clienteIds()) {
            Cliente c = porId.get(id);
            if (c == null) {
                throw new ReglaNegocioException(
                        "El cliente " + id + " no pertenece a la ruta " + rutaId);
            }
            c.setOrdenVisita(orden++);
            mencionados.add(id);
        }

        // Los que no vinieron en la lista se quedan sin orden, no al final.
        for (Cliente c : deLaRuta) {
            if (!mencionados.contains(c.getId())) {
                c.setOrdenVisita(null);
            }
        }

        cRepository.saveAll(deLaRuta);
    }

    /** null = ruta sin repartidor. 404 si el id no existe, 422 si existe pero no es repartidor. */
    private Usuario buscarRepartidor(Long id) {
        if (id == null) {
            return null;
        }

        Usuario u = uRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el usuario " + id));

        if (u.getRol() != RolUsuario.REPARTIDOR) {
            throw new ReglaNegocioException(
                    "El usuario " + u.getNombre() + " no es repartidor, es " + u.getRol());
        }

        return u;
    }
}
