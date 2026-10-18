package pe.com.canchadirecta.engagement.adapter.in.rest.dto;

import java.util.List;

/**
 * Calificaciones de una cancha: total, promedio y la pagina mas reciente (CU02.5).
 */
public record CalificacionesCanchaResponse(
        Long canchaId,
        long total,
        double promedio,
        List<CalificacionResponse> calificaciones
) {
}
