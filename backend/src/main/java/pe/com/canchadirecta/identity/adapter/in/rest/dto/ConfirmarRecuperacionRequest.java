package pe.com.canchadirecta.identity.adapter.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cuerpo de la peticion POST /api/v1/auth/password-reset/confirm. */
public record ConfirmarRecuperacionRequest(
        @NotBlank String token,
        @NotBlank @Size(max = 100) String nuevaPassword,
        @NotBlank @Size(max = 100) String confirmacionPassword
) {
}
