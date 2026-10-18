package pe.com.canchadirecta.engagement.application.port.out;

import pe.com.canchadirecta.engagement.application.dto.ResumenCalificaciones;
import pe.com.canchadirecta.engagement.domain.model.Calificacion;

/**
 * Puerto de salida de calificaciones (SQL nativo).
 */
public interface CalificacionRepositorio {

    /** Indica si una reserva ya tiene calificacion (RN-04). */
    boolean existeParaReserva(Long idReserva);

    /** Inserta la calificacion y devuelve la copia con su id generado. */
    Calificacion registrar(Calificacion calificacion);

    /** Total, promedio y calificaciones mas recientes de una cancha. */
    ResumenCalificaciones resumenDeCancha(Long idCancha, int limite);
}
