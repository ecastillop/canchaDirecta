package pe.com.canchadirecta.engagement.application.port.in;

import pe.com.canchadirecta.engagement.domain.model.Reclamo;
import pe.com.canchadirecta.engagement.domain.model.ReclamoSeguimiento;

import java.util.List;

/**
 * Moderacion de reclamos por la administracion de plataforma (F07 sobre CU06.6).
 */
public interface ModerarReclamosUseCase {

    /** Todos los reclamos, opcionalmente filtrados por estado. */
    List<Reclamo> listar(String estado, int limite);

    /** Detalle de cualquier reclamo con su conversacion. */
    ReclamosUseCase.Detalle detalle(Long idReclamo);

    /** Cambia el estado con transiciones validas, avisa al titular y anota el comentario si viene. */
    Reclamo cambiarEstado(Long idReclamo, Long idAdmin, String estado, String comentario);

    /** Respuesta oficial de la administracion; avisa al titular. */
    ReclamoSeguimiento responder(Long idReclamo, Long idAdmin, String comentario);
}
