package pe.com.canchadirecta.settlements.adapter.in.rest.dto;

import jakarta.validation.constraints.NotBlank;

/** Cuerpo de `POST /api/v1/liquidaciones`. Las fechas van en formato `AAAA-MM-DD`. */
public record GenerarLiquidacionRequest(
        @NotBlank String periodoInicio,
        @NotBlank String periodoFin
) {
}
