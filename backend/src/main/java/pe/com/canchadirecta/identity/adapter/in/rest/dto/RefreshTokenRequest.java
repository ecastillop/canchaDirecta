package pe.com.canchadirecta.identity.adapter.in.rest.dto;

import jakarta.validation.constraints.NotBlank;

/** Cuerpo de la peticion POST /api/v1/auth/refresh. */
public record RefreshTokenRequest(
        @NotBlank String refreshToken
) {
}
