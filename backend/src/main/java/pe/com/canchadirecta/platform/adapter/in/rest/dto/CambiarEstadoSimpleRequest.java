package pe.com.canchadirecta.platform.adapter.in.rest.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Cambio de estado simple (`ACTIVA`/`INACTIVA`) para promociones y cocheras.
 */
public record CambiarEstadoSimpleRequest(
        @NotBlank(message = "El estado es obligatorio.")
        String estado
) {
}
