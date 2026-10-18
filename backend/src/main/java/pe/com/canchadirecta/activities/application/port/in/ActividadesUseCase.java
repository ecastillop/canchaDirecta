package pe.com.canchadirecta.activities.application.port.in;

import pe.com.canchadirecta.activities.application.dto.ActividadCommand;
import pe.com.canchadirecta.activities.application.dto.InscripcionVista;
import pe.com.canchadirecta.activities.domain.model.Actividad;

import java.time.Instant;
import java.util.List;

/** Casos de uso de actividades y sus inscripciones. */
public interface ActividadesUseCase {

    List<Actividad> publicas(Instant desde, int limite);

    Actividad detalle(Long idActividad);

    List<Actividad> delEstablecimiento(Long idUsuario, Long idEstablecimiento);

    /** Listado global para administracion, incluidas las canceladas. */
    List<Actividad> todas(int limite);

    void cancelarComoAdmin(Long idActividad);

    Actividad crear(Long idUsuario, Long idEstablecimiento, ActividadCommand command);

    Actividad actualizar(Long idUsuario, Long idActividad, ActividadCommand command);

    void cancelar(Long idUsuario, Long idActividad);

    InscripcionVista inscribir(Long idUsuario, Long idActividad);

    void cancelarInscripcion(Long idUsuario, Long idActividad);

    List<InscripcionVista> misInscripciones(Long idUsuario);

    List<InscripcionVista> inscritos(Long idUsuario, Long idActividad);
}
