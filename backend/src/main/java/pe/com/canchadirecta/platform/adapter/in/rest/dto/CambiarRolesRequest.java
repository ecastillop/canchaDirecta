package pe.com.canchadirecta.platform.adapter.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Reemplazo completo de los roles de un usuario. No se puede dejar la plataforma sin
 * administradores activos.
 */
public record CambiarRolesRequest(
        @NotEmpty(message = "Debe indicar al menos un rol.")
        List<@NotBlank(message = "El rol no puede estar vacio.") String> roles
) {
}
