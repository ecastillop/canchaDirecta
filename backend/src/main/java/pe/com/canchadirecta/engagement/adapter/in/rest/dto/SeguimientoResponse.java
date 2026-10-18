package pe.com.canchadirecta.engagement.adapter.in.rest.dto;

import pe.com.canchadirecta.engagement.domain.model.ReclamoSeguimiento;

import java.time.Instant;

/**
 * Comentario de la conversacion de un reclamo.
 */
public record SeguimientoResponse(
        Long id,
        String comentario,
        Instant fecha
) {

    public static SeguimientoResponse de(ReclamoSeguimiento seguimiento) {
        return new SeguimientoResponse(seguimiento.id(), seguimiento.comentario(), seguimiento.fecha());
    }
}
