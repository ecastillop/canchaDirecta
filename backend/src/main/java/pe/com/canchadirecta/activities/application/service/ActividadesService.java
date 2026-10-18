package pe.com.canchadirecta.activities.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.com.canchadirecta.activities.application.dto.ActividadCommand;
import pe.com.canchadirecta.activities.application.dto.InscripcionVista;
import pe.com.canchadirecta.activities.application.port.in.ActividadesUseCase;
import pe.com.canchadirecta.activities.application.port.out.ActividadRepositorio;
import pe.com.canchadirecta.activities.domain.model.Actividad;
import pe.com.canchadirecta.activities.domain.model.Inscripcion;
import pe.com.canchadirecta.shared.domain.DomainException;
import pe.com.canchadirecta.shared.domain.ErrorCodes;

import java.time.Instant;
import java.util.List;

/**
 * Actividades de un establecimiento y sus inscripciones. El propietario administra las
 * actividades de sus espacios; cualquier usuario autenticado puede inscribirse.
 */
@ApplicationScoped
public class ActividadesService implements ActividadesUseCase {

    private final ActividadRepositorio repositorio;

    @Inject
    public ActividadesService(ActividadRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    public List<Actividad> publicas(Instant desde, int limite) {
        return repositorio.listarPublicas(desde == null ? Instant.now() : desde, limite);
    }

    @Override
    public Actividad detalle(Long idActividad) {
        return exigir(idActividad);
    }

    @Override
    public List<Actividad> delEstablecimiento(Long idUsuario, Long idEstablecimiento) {
        exigirPropietario(idEstablecimiento, idUsuario);
        return repositorio.listarPorEstablecimiento(idEstablecimiento);
    }

    @Override
    public List<Actividad> todas(int limite) {
        return repositorio.listarTodas(limite);
    }

    @Override
    @Transactional
    public void cancelarComoAdmin(Long idActividad) {
        exigir(idActividad);
        if (!repositorio.cancelar(idActividad)) {
            throw new DomainException(ErrorCodes.CONFLICTO, "La actividad ya estaba cancelada.");
        }
    }

    @Override
    @Transactional
    public Actividad crear(Long idUsuario, Long idEstablecimiento, ActividadCommand command) {
        exigirPropietario(idEstablecimiento, idUsuario);
        Actividad actividad = new Actividad(null, idEstablecimiento, null, null, command.nombre(),
                command.descripcion(), command.inicio(), command.fin(), command.cupos(),
                command.precio(), Actividad.Estado.ACTIVA, 0);
        Long id = repositorio.crear(actividad);
        return exigir(id);
    }

    @Override
    @Transactional
    public Actividad actualizar(Long idUsuario, Long idActividad, ActividadCommand command) {
        Actividad actual = exigir(idActividad);
        exigirPropietario(actual.idEstablecimiento(), idUsuario);
        if (actual.estado() != Actividad.Estado.ACTIVA) {
            throw new DomainException(ErrorCodes.CONFLICTO, "No se puede editar una actividad cancelada.");
        }
        if (command.cupos() < actual.inscritos()) {
            throw new DomainException(ErrorCodes.CONFLICTO,
                    "Los cupos no pueden ser menores que las inscripciones confirmadas ("
                            + actual.inscritos() + ").");
        }
        Actividad actualizada = new Actividad(actual.id(), actual.idEstablecimiento(),
                actual.establecimiento(), actual.distrito(), command.nombre(), command.descripcion(),
                command.inicio(), command.fin(), command.cupos(), command.precio(), actual.estado(),
                actual.inscritos());
        repositorio.actualizar(actualizada);
        return exigir(idActividad);
    }

    @Override
    @Transactional
    public void cancelar(Long idUsuario, Long idActividad) {
        Actividad actual = exigir(idActividad);
        exigirPropietario(actual.idEstablecimiento(), idUsuario);
        if (!repositorio.cancelar(idActividad)) {
            throw new DomainException(ErrorCodes.CONFLICTO, "La actividad ya estaba cancelada.");
        }
    }

    @Override
    @Transactional
    public InscripcionVista inscribir(Long idUsuario, Long idActividad) {
        Actividad actividad = exigir(idActividad);
        Instant ahora = Instant.now();
        if (!actividad.admiteInscripciones(ahora)) {
            throw new DomainException(ErrorCodes.CONFLICTO,
                    "La actividad ya no admite inscripciones.");
        }
        var existente = repositorio.buscarInscripcion(idActividad, idUsuario);
        if (existente.isPresent()) {
            if (existente.get().estado() == Inscripcion.Estado.CONFIRMADA) {
                throw new DomainException(ErrorCodes.CONFLICTO, "Ya estas inscrito en esta actividad.");
            }
            Inscripcion reactivada = repositorio.reactivarInscripcion(idActividad, idUsuario, ahora)
                    .orElseThrow(() -> new DomainException(ErrorCodes.CONFLICTO,
                            "No quedan cupos disponibles para esta actividad."));
            return new InscripcionVista(reactivada, exigir(idActividad), null, null);
        }
        Inscripcion creada = repositorio.crearInscripcion(idActividad, idUsuario, ahora)
                .orElseThrow(() -> new DomainException(ErrorCodes.CONFLICTO,
                        "No quedan cupos disponibles para esta actividad."));
        return new InscripcionVista(creada, exigir(idActividad), null, null);
    }

    @Override
    @Transactional
    public void cancelarInscripcion(Long idUsuario, Long idActividad) {
        if (!repositorio.cancelarInscripcion(idActividad, idUsuario)) {
            throw new DomainException(ErrorCodes.NO_ENCONTRADO,
                    "No tienes una inscripcion activa en esta actividad.");
        }
    }

    @Override
    public List<InscripcionVista> misInscripciones(Long idUsuario) {
        return repositorio.listarInscripcionesDeUsuario(idUsuario);
    }

    @Override
    public List<InscripcionVista> inscritos(Long idUsuario, Long idActividad) {
        Actividad actividad = exigir(idActividad);
        exigirPropietario(actividad.idEstablecimiento(), idUsuario);
        return repositorio.listarInscripcionesDeActividad(idActividad);
    }

    private Actividad exigir(Long idActividad) {
        return repositorio.buscar(idActividad)
                .orElseThrow(() -> new DomainException(ErrorCodes.NO_ENCONTRADO,
                        "La actividad no existe."));
    }

    private void exigirPropietario(Long idEstablecimiento, Long idUsuario) {
        if (!repositorio.esPropietarioDe(idEstablecimiento, idUsuario)) {
            throw new DomainException(ErrorCodes.SIN_PERMISO,
                    "Solo el propietario del establecimiento puede gestionar sus actividades.");
        }
    }
}
