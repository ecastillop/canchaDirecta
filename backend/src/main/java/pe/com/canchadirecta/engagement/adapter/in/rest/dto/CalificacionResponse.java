package pe.com.canchadirecta.engagement.adapter.in.rest.dto;

import java.time.Instant;

/**
 * Representacion publica de una calificacion. No expone al autor: la calificacion cuelga
 * de la reserva y el titular se deduce del token, no del cuerpo.
 */
public record CalificacionResponse(
        Long id,
        Long reservaId,
        int puntaje,
        String comentario,
        Instant fechaRegistro
) {
}
