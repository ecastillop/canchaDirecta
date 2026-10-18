package pe.com.canchadirecta.bookings.adapter.in.rest.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Disponibilidad de una cancha en una fecha local. `fecha` es la fecha consultada en
 * la zona `zonaHoraria`; cada franja se expresa como instante UTC.
 */
public record DisponibilidadResponse(
        Long canchaId,
        String fecha,
        String zonaHoraria,
        long duracionMinutos,
        List<Franja> franjas
) {

    /** Bloque libre reservable junto con el precio aplicable. */
    public record Franja(Instant inicio, Instant fin, BigDecimal precio) {
    }
}
