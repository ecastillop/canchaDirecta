package pe.com.canchadirecta.bookings.adapter.in.rest.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/** Cuerpo de POST /api/v1/reservas/{id}/modificar (CU03.7; misma duracion, nueva franja). */
public record ModificarReservaRequest(
        @NotNull Instant inicio,
        @NotNull Instant fin
) {
}
