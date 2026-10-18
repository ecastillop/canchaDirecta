package pe.com.canchadirecta.engagement.adapter.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import pe.com.canchadirecta.engagement.domain.model.ReclamoSeguimiento;

/**
 * Cambio de estado de un reclamo por la administracion, con comentario opcional que queda
 * como seguimiento.
 */
public record CambiarEstadoReclamoRequest(
        @NotBlank(message = "El estado es obligatorio.")
        String estado,
        @Size(max = ReclamoSeguimiento.COMENTARIO_MAX,
                message = "El comentario no puede superar los 800 caracteres.")
        String comentario
) {
}
