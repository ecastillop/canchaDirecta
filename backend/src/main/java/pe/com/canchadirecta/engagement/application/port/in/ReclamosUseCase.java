package pe.com.canchadirecta.engagement.application.port.in;

import pe.com.canchadirecta.engagement.domain.model.Reclamo;
import pe.com.canchadirecta.engagement.domain.model.ReclamoSeguimiento;

import java.util.List;

/**
 * Casos de uso de soporte del usuario (CU06.6): crear, listar y consultar sus reclamos y
 * anadir seguimientos.
 */
public interface ReclamosUseCase {

    /** Crea un reclamo; si trae reserva, debe ser del usuario. */
    Reclamo crear(Long idUsuario, Long idReserva, String asunto, String descripcion);

    /** Reclamos del usuario, los mas recientes primero. */
    List<Reclamo> mios(Long idUsuario, int limite);

    /** Reclamo con sus seguimientos; `403` si es de otro usuario. */
    Detalle detalle(Long idReclamo, Long idUsuario);

    /** Anade un comentario a un reclamo propio que no este cerrado. */
    ReclamoSeguimiento comentar(Long idReclamo, Long idUsuario, String comentario);

    /** Reclamo y su conversacion. */
    record Detalle(Reclamo reclamo, List<ReclamoSeguimiento> seguimientos) {
    }
}
