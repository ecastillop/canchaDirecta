package pe.com.canchadirecta.venues.adapter.in.rest.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Cuerpo de POST /api/v1/establecimientos/{id}/canchas. */
public record CrearCanchaRequest(
        @NotBlank @Size(max = 100) String nombre,
        @Size(max = 500) String descripcion,
        @NotNull @Min(1) @Max(100) Integer capacidad,
        @NotNull @PositiveOrZero BigDecimal precioBase
) {
}
