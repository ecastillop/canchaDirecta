package pe.com.canchadirecta.identity.adapter.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cuerpo de POST /api/v1/auth/email/resend. */
public record ReenviarVerificacionRequest(
        @NotBlank @Size(max = 150) String correo
) {
}
