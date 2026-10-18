package pe.com.canchadirecta.identity.adapter.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cuerpo de PUT /api/v1/users/me (CU01.5). */
public record ActualizarPerfilRequest(
        @NotBlank @Size(max = 80) String nombres,
        @NotBlank @Size(max = 80) String apellidos,
        @Size(max = 20) String telefono
) {
}
