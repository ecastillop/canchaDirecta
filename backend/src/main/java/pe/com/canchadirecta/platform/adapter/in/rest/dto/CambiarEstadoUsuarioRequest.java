package pe.com.canchadirecta.platform.adapter.in.rest.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Cambio de estado de una cuenta: `ACTIVO`, `INACTIVO` o `BLOQUEADO`.
 */
public record CambiarEstadoUsuarioRequest(
        @NotBlank(message = "El estado es obligatorio.")
        String estado
) {
}
