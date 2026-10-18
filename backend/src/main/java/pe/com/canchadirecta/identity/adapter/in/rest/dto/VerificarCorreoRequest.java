package pe.com.canchadirecta.identity.adapter.in.rest.dto;

import jakarta.validation.constraints.NotBlank;

/** Cuerpo de POST /api/v1/auth/email/verify. */
public record VerificarCorreoRequest(
        @NotBlank String token
) {
}
