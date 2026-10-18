package pe.com.canchadirecta.venues.adapter.in.rest.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Alta de una foto de la cancha; `orden` es unico por cancha.
 */
public record CrearFotoRequest(
        @NotBlank(message = "La URL es obligatoria.")
        @Size(max = 500, message = "La URL no puede superar 500 caracteres.")
        String url,
        @Min(value = 1, message = "El orden minimo es 1.")
        @Max(value = 99, message = "El orden maximo es 99.")
        int orden,
        boolean principal
) {
}
