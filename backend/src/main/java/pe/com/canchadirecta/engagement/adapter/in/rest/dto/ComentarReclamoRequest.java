package pe.com.canchadirecta.engagement.adapter.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import pe.com.canchadirecta.engagement.domain.model.ReclamoSeguimiento;

/**
 * Comentario que el usuario anade a la conversacion de su reclamo.
 */
public record ComentarReclamoRequest(
        @NotBlank(message = "El comentario es obligatorio.")
        @Size(max = ReclamoSeguimiento.COMENTARIO_MAX,
                message = "El comentario no puede superar los 800 caracteres.")
        String comentario
) {
}
