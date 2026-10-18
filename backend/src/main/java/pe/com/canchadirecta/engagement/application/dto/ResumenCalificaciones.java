package pe.com.canchadirecta.engagement.application.dto;

import pe.com.canchadirecta.engagement.domain.model.Calificacion;

import java.util.List;

/**
 * Calificaciones de una cancha con su promedio y total (CU02.5 "ver calificaciones").
 *
 * <p>`total` es el numero global de calificaciones de la cancha; `calificaciones` es la
 * pagina mas reciente (acotada por el limite de la consulta). `promedio` es 0.0 cuando la
 * cancha aun no tiene calificaciones.</p>
 */
public record ResumenCalificaciones(
        Long idCancha,
        long total,
        double promedio,
        List<Calificacion> calificaciones
) {
}
