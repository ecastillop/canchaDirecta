package pe.com.canchadirecta.platform.adapter.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cuerpo de POST /api/v1/propietarios/solicitudes (CU01.6). */
public record SolicitarPropietarioRequest(
        @NotBlank @Size(max = 20) String documentoIdentidad
) {
}
