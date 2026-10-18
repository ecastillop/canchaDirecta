package pe.com.canchadirecta.activities.adapter.in.rest.dto;

import pe.com.canchadirecta.activities.application.dto.InscripcionVista;

import java.time.Instant;

/** Inscripcion con los datos de su actividad y, si aplica, del cliente inscrito. */
public record InscripcionResponse(
        Long id,
        Long idActividad,
        String actividad,
        String establecimiento,
        Instant inicio,
        Instant fin,
        String estado,
        Instant fechaRegistro,
        String nombreUsuario,
        String correoUsuario
) {

    public static InscripcionResponse de(InscripcionVista vista) {
        return new InscripcionResponse(
                vista.inscripcion().id(),
                vista.inscripcion().idActividad(),
                vista.actividad().nombre(),
                vista.actividad().establecimiento(),
                vista.actividad().inicio(),
                vista.actividad().fin(),
                vista.inscripcion().estado().name(),
                vista.inscripcion().fechaRegistro(),
                vista.nombreUsuario(),
                vista.correoUsuario());
    }
}
