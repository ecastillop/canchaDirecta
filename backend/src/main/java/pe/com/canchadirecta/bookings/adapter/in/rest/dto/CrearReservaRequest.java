package pe.com.canchadirecta.bookings.adapter.in.rest.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/** Cuerpo de POST /api/v1/reservas (fechas en ISO-8601 UTC, p. ej. 2026-11-02T18:00:00Z). */
public record CrearReservaRequest(
        @NotNull Long canchaId,
        @NotNull Instant inicio,
        @NotNull Instant fin,
        /* Cochera opcional (RN-08); `null` reserva solo la cancha. */
        Long cocheraId,
        /* Codigo de promocion opcional (CU04.6); el descuento se aplica al monto de la cancha. */
        String codigoPromocion
) {
}
