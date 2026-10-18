package pe.com.canchadirecta.engagement.adapter.in.rest.dto;

import pe.com.canchadirecta.engagement.application.port.in.ReclamosUseCase;
import pe.com.canchadirecta.engagement.domain.model.Reclamo;

import java.time.Instant;
import java.util.List;

/**
 * Detalle de un reclamo: sus datos y la conversacion completa.
 */
public record ReclamoDetalleResponse(
        Long id,
        Long idReserva,
        String asunto,
        String descripcion,
        String estado,
        Instant fechaRegistro,
        List<SeguimientoResponse> seguimientos
) {

    public static ReclamoDetalleResponse de(ReclamosUseCase.Detalle detalle) {
        Reclamo reclamo = detalle.reclamo();
        return new ReclamoDetalleResponse(
                reclamo.id(), reclamo.idReserva(), reclamo.asunto(), reclamo.descripcion(),
                reclamo.estado().name(), reclamo.fechaRegistro(),
                detalle.seguimientos().stream().map(SeguimientoResponse::de).toList());
    }
}
