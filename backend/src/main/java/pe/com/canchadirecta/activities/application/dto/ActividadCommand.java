package pe.com.canchadirecta.activities.application.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** Datos de alta o edicion de una actividad. */
public record ActividadCommand(
        String nombre,
        String descripcion,
        Instant inicio,
        Instant fin,
        int cupos,
        BigDecimal precio
) {
}
