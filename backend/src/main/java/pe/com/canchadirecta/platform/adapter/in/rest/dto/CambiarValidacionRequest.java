package pe.com.canchadirecta.platform.adapter.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Validacion o rechazo de un propietario. El motivo es obligatorio al rechazar.
 */
public record CambiarValidacionRequest(
        @NotBlank(message = "El estado es obligatorio.")
        String estado,
        @Size(max = 500, message = "El motivo no puede superar los 500 caracteres.")
        String motivo
) {
}
