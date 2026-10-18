package pe.com.canchadirecta.activities.application.port.out;

import pe.com.canchadirecta.activities.application.dto.InscripcionVista;
import pe.com.canchadirecta.activities.domain.model.Actividad;
import pe.com.canchadirecta.activities.domain.model.Inscripcion;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Persistencia de actividades e inscripciones con SQL nativo (ADR-0012). */
public interface ActividadRepositorio {

    List<Actividad> listarPublicas(Instant desde, int limite);

    Optional<Actividad> buscar(Long idActividad);

    List<Actividad> listarPorEstablecimiento(Long idEstablecimiento);

    /** Todas las actividades (incluidas canceladas) para administracion. */
    List<Actividad> listarTodas(int limite);

    Long crear(Actividad actividad);

    boolean actualizar(Actividad actividad);

    boolean cancelar(Long idActividad);

    boolean esPropietarioDe(Long idEstablecimiento, Long idUsuario);

    Optional<Inscripcion> buscarInscripcion(Long idActividad, Long idUsuario);

    long contarInscritos(Long idActividad);

    /** Crea la inscripcion si la actividad esta vigente y quedan cupos; vacio si no hay cupo. */
    Optional<Inscripcion> crearInscripcion(Long idActividad, Long idUsuario, Instant ahora);

    /** Reactiva una inscripcion cancelada si siguen habiendo cupos. */
    Optional<Inscripcion> reactivarInscripcion(Long idActividad, Long idUsuario, Instant ahora);

    boolean cancelarInscripcion(Long idActividad, Long idUsuario);

    List<InscripcionVista> listarInscripcionesDeUsuario(Long idUsuario);

    List<InscripcionVista> listarInscripcionesDeActividad(Long idActividad);
}
