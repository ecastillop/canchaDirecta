package pe.com.canchadirecta.identity.adapter.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cuerpo de la peticion POST /api/v1/auth/login. */
public record IniciarSesionRequest(
        @NotBlank @Size(max = 150) String correo,
        @NotBlank @Size(max = 100) String password
) {
}
