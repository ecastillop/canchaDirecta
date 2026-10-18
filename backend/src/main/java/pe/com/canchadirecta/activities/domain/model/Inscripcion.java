package pe.com.canchadirecta.activities.domain.model;

import java.time.Instant;

/** Inscripcion de un cliente a una actividad. Un usuario solo puede tener una por actividad. */
public record Inscripcion(
        Long id,
        Long idActividad,
        Long idUsuario,
        Estado estado,
        Instant fechaRegistro
) {

    public enum Estado {
        CONFIRMADA, CANCELADA
    }
}
