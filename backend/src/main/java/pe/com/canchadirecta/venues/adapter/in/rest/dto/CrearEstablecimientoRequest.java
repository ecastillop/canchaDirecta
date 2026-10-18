package pe.com.canchadirecta.venues.adapter.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Cuerpo de POST /api/v1/establecimientos. */
public record CrearEstablecimientoRequest(
        @NotBlank @Size(max = 20) String documentoIdentidad,
        @NotNull Long idDistrito,
        @NotBlank @Size(max = 120) String nombre,
        @NotBlank @Size(max = 200) String direccion,
        @Size(max = 200) String referencia
) {
}
