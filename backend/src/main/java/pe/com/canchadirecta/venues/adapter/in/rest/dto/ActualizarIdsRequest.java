package pe.com.canchadirecta.venues.adapter.in.rest.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Reemplazo del conjunto de deportes o caracteristicas de una cancha (lista vacia = limpiar).
 */
public record ActualizarIdsRequest(
        @NotNull(message = "La lista de ids es obligatoria.")
        List<Long> ids
) {
}
