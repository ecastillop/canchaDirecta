package pe.com.canchadirecta.engagement.application.port.out;

import pe.com.canchadirecta.engagement.domain.model.Reclamo;
import pe.com.canchadirecta.engagement.domain.model.ReclamoSeguimiento;

import java.util.List;
import java.util.Optional;

/**
 * Puerto de salida de reclamos (SQL nativo, ADR-0012).
 */
public interface ReclamoRepositorio {

    /** Inserta el reclamo y devuelve la copia con su id generado. */
    Reclamo registrar(Reclamo reclamo);

    /** Reclamo por id, sin seguimientos. */
    Optional<Reclamo> buscar(Long idReclamo);

    /** Reclamos del usuario, los mas recientes primero. */
    List<Reclamo> listarPorUsuario(Long idUsuario, int limite);

    /** Todos los reclamos (moderacion), filtrados opcionalmente por estado, los mas recientes primero. */
    List<Reclamo> listar(Reclamo.Estado estado, int limite);

    /** Cambia el estado solo si sigue en el estado esperado; `false` si otra transicion gano. */
    boolean cambiarEstado(Long idReclamo, Reclamo.Estado esperado, Reclamo.Estado nuevo);

    /** Seguimientos de un reclamo en orden cronologico. */
    List<ReclamoSeguimiento> seguimientos(Long idReclamo);

    /** Anade un comentario y devuelve la copia con su id generado. */
    ReclamoSeguimiento agregarSeguimiento(ReclamoSeguimiento seguimiento);
}
