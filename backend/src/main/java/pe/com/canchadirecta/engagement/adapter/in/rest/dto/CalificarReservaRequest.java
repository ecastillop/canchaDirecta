package pe.com.canchadirecta.engagement.adapter.in.rest.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import pe.com.canchadirecta.engagement.domain.model.Calificacion;

/**
 * Calificacion de una reserva: puntaje obligatorio (1..5) y comentario opcional
 * (hasta 500 caracteres).
 */
public record CalificarReservaRequest(
        @Min(value = Calificacion.PUNTAJE_MIN, message = "El puntaje minimo es 1.")
        @Max(value = Calificacion.PUNTAJE_MAX, message = "El puntaje maximo es 5.")
        int puntaje,
        @Size(max = Calificacion.COMENTARIO_MAX,
                message = "El comentario no puede superar los 500 caracteres.")
        String comentario
) {
}
