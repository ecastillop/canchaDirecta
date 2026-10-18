package pe.com.canchadirecta.engagement.adapter.in.rest.dto;

import pe.com.canchadirecta.engagement.domain.model.Reclamo;

import java.time.Instant;

/**
 * Representacion publica de un reclamo del usuario.
 */
public record ReclamoResponse(
        Long id,
        Long idReserva,
        String asunto,
        String descripcion,
        String estado,
        Instant fechaRegistro
) {

    public static ReclamoResponse de(Reclamo reclamo) {
        return new ReclamoResponse(reclamo.id(), reclamo.idReserva(), reclamo.asunto(),
                reclamo.descripcion(), reclamo.estado().name(), reclamo.fechaRegistro());
    }
}
