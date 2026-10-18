package pe.com.canchadirecta.venues.application.dto;

import java.time.Instant;

/** Datos para registrar un bloqueo operativo de cancha (RN-03). */
public record RegistrarBloqueoCommand(
        Long idCancha,
        Instant inicio,
        Instant fin,
        String motivo
) {
}
