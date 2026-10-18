package pe.com.canchadirecta.engagement.adapter.in.rest.dto;

import pe.com.canchadirecta.engagement.application.port.in.ReclamosUseCase;
import pe.com.canchadirecta.engagement.domain.model.Reclamo;

import java.time.Instant;
import java.util.List;

/**
 * Detalle de un reclamo para moderacion, con toda la conversacion.
 */
public record AdminReclamoDetalleResponse(
        Long id,
        Long idUsuario,
        Long idReserva,
        String asunto,
        String descripcion,
        String estado,
        Instant fechaRegistro,
        List<SeguimientoResponse> seguimientos
) {

    public static AdminReclamoDetalleResponse de(ReclamosUseCase.Detalle detalle) {
        Reclamo reclamo = detalle.reclamo();
        return new AdminReclamoDetalleResponse(
                reclamo.id(), reclamo.idUsuario(), reclamo.idReserva(), reclamo.asunto(),
                reclamo.descripcion(), reclamo.estado().name(), reclamo.fechaRegistro(),
                detalle.seguimientos().stream().map(SeguimientoResponse::de).toList());
    }
}
