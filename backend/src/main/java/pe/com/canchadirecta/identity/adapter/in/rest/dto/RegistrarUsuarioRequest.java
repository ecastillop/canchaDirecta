package pe.com.canchadirecta.identity.adapter.in.rest.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cuerpo de la peticion POST /api/v1/auth/register. */
public record RegistrarUsuarioRequest(
        @NotBlank @Size(max = 80) String nombres,
        @NotBlank @Size(max = 80) String apellidos,
        @NotBlank @Email @Size(max = 150) String correo,
        @Size(max = 20) String telefono,
        @NotBlank @Size(min = 8, max = 100) String password,
        @NotBlank String confirmacionPassword
) {
}
