package pe.com.canchadirecta.identity.adapter.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cuerpo de la peticion POST /api/v1/auth/password-reset/request. */
public record SolicitarRecuperacionRequest(
        @NotBlank @Size(max = 150) String correo
) {
}
