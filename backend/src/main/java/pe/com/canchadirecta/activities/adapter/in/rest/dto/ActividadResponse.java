package pe.com.canchadirecta.activities.adapter.in.rest.dto;

import pe.com.canchadirecta.activities.domain.model.Actividad;

import java.math.BigDecimal;
import java.time.Instant;

/** Representacion publica de una actividad y su ocupacion actual. */
public record ActividadResponse(
        Long id,
        Long idEstablecimiento,
        String establecimiento,
        String distrito,
        String nombre,
        String descripcion,
        Instant inicio,
        Instant fin,
        int cupos,
        int inscritos,
        int cuposDisponibles,
        BigDecimal precio,
        String estado
) {

    public static ActividadResponse de(Actividad actividad) {
        return new ActividadResponse(actividad.id(), actividad.idEstablecimiento(),
                actividad.establecimiento(), actividad.distrito(), actividad.nombre(),
                actividad.descripcion(), actividad.inicio(), actividad.fin(), actividad.cupos(),
                (int) actividad.inscritos(), actividad.cuposDisponibles(), actividad.precio(),
                actividad.estado().name());
    }
}
