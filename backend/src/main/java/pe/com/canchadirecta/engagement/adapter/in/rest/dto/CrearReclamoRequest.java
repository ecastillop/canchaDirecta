package pe.com.canchadirecta.engagement.adapter.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import pe.com.canchadirecta.engagement.domain.model.Reclamo;

/**
 * Alta de un reclamo: reserva opcional (debe ser del usuario), asunto y descripcion.
 */
public record CrearReclamoRequest(
        Long idReserva,
        @NotBlank(message = "El asunto es obligatorio.")
        @Size(max = Reclamo.ASUNTO_MAX, message = "El asunto no puede superar los 150 caracteres.")
        String asunto,
        @NotBlank(message = "La descripcion es obligatoria.")
        @Size(max = Reclamo.DESCRIPCION_MAX, message = "La descripcion no puede superar los 800 caracteres.")
        String descripcion
) {
}
