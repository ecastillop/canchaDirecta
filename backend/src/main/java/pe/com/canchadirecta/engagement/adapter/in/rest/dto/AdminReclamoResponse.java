package pe.com.canchadirecta.engagement.adapter.in.rest.dto;

import pe.com.canchadirecta.engagement.domain.model.Reclamo;

import java.time.Instant;

/**
 * Reclamo visto por la administracion: incluye el titular para poder responderle.
 */
public record AdminReclamoResponse(
        Long id,
        Long idUsuario,
        Long idReserva,
        String asunto,
        String descripcion,
        String estado,
        Instant fechaRegistro
) {

    public static AdminReclamoResponse de(Reclamo reclamo) {
        return new AdminReclamoResponse(reclamo.id(), reclamo.idUsuario(), reclamo.idReserva(),
                reclamo.asunto(), reclamo.descripcion(), reclamo.estado().name(), reclamo.fechaRegistro());
    }
}
