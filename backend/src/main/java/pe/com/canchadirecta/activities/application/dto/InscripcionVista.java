package pe.com.canchadirecta.activities.application.dto;

import pe.com.canchadirecta.activities.domain.model.Actividad;
import pe.com.canchadirecta.activities.domain.model.Inscripcion;

/** Inscripcion con su actividad y, para el propietario, los datos del cliente. */
public record InscripcionVista(
        Inscripcion inscripcion,
        Actividad actividad,
        String nombreUsuario,
        String correoUsuario
) {
}
