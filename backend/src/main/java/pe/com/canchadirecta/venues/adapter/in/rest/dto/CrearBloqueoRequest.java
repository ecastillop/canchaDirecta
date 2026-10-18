package pe.com.canchadirecta.venues.adapter.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Cuerpo de POST /api/v1/canchas/{idCancha}/bloqueos (instantes ISO-8601 UTC, p. ej.
 * 2026-11-02T18:00:00Z).
 */
public record CrearBloqueoRequest(
        @NotNull Instant inicio,
        @NotNull Instant fin,
        @NotBlank @Size(max = 200) String motivo
) {
}
