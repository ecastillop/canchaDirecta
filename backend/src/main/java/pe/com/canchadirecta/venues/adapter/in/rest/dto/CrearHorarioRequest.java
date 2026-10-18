package pe.com.canchadirecta.venues.adapter.in.rest.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalTime;

/**
 * Cuerpo de POST /api/v1/canchas/{idCancha}/horarios. La hora es local del
 * establecimiento y `diaSemana` usa la numeracion ISO (1 = lunes ... 7 = domingo).
 */
public record CrearHorarioRequest(
        @NotNull @Min(1) @Max(7) Integer diaSemana,
        @NotNull LocalTime horaInicio,
        @NotNull LocalTime horaFin,
        @PositiveOrZero BigDecimal precio
) {
}
