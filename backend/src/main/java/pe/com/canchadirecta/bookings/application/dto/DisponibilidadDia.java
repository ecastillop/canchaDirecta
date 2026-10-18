package pe.com.canchadirecta.bookings.application.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Disponibilidad de una cancha en una fecha local. Las franjas se devuelven en UTC
 * (instantes absolutos); `zonaHoraria` indica la zona con la que se interpreto la
 * fecha solicitada.
 */
public record DisponibilidadDia(
        Long canchaId,
        LocalDate fecha,
        String zonaHoraria,
        long duracionMinutos,
        List<FranjaDisponible> franjas,
        List<OcupacionFranja> ocupadas
) {
}
