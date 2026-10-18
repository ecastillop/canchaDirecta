package pe.com.canchadirecta.venues.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.com.canchadirecta.shared.domain.DomainException;
import pe.com.canchadirecta.shared.domain.ErrorCodes;
import pe.com.canchadirecta.venues.application.dto.ActualizarCanchaCommand;
import pe.com.canchadirecta.venues.application.dto.ActualizarEstablecimientoCommand;
import pe.com.canchadirecta.venues.application.dto.RegistrarCanchaCommand;
import pe.com.canchadirecta.venues.application.dto.RegistrarCocheraCommand;
import pe.com.canchadirecta.venues.application.dto.RegistrarEstablecimientoCommand;
import pe.com.canchadirecta.venues.application.port.in.GestionVenuesUseCase;
import pe.com.canchadirecta.venues.application.port.out.VenuesRepositorio;
import pe.com.canchadirecta.venues.domain.model.Cancha;
import pe.com.canchadirecta.venues.domain.model.Cochera;
import pe.com.canchadirecta.venues.domain.model.Establecimiento;

import java.util.List;

/**
 * Registro de establecimientos, canchas y cocheras. Un usuario que registra un
 * establecimiento pasa a ser su propietario (se crea el `propietario` si no existia).
 */
@ApplicationScoped
public class GestionVenuesService implements GestionVenuesUseCase {

    private final VenuesRepositorio repositorio;

    @Inject
    public GestionVenuesService(VenuesRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    @Transactional
    public Long registrarEstablecimiento(Long idUsuario, RegistrarEstablecimientoCommand command) {
        if (command.documentoIdentidad() == null || command.documentoIdentidad().isBlank()) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS,
                    "El documento de identidad es obligatorio para registrar un establecimiento.");
        }
        Long idPropietario = repositorio.asegurarPropietario(idUsuario, command.documentoIdentidad());
        return repositorio.crearEstablecimiento(idPropietario, command.idDistrito(),
                command.nombre(), command.direccion(), command.referencia());
    }

    @Override
    @Transactional
    public Long registrarCancha(Long idUsuario, RegistrarCanchaCommand command) {
        if (command.idEstablecimiento() == null) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "El establecimiento es obligatorio.");
        }
        if (!repositorio.esPropietarioDe(command.idEstablecimiento(), idUsuario)) {
            throw new DomainException(ErrorCodes.SIN_PERMISO,
                    "Solo el propietario puede registrar canchas en este establecimiento.");
        }
        // La construccion del agregado aplica las invariantes (nombre, capacidad, precio).
        Cancha validada = new Cancha(null, command.idEstablecimiento(), command.nombre(),
                command.capacidad(), command.precioBase(), Cancha.Estado.ACTIVA);

        return repositorio.crearCancha(validada.idEstablecimiento(), validada.nombre(),
                command.descripcion(), validada.capacidad(), validada.precioBase());
    }

    @Override
    public List<Cancha> listarCanchas(Long idDistrito, int limite) {
        return repositorio.listarCanchas(idDistrito, limite);
    }

    @Override
    public Establecimiento obtenerEstablecimiento(Long idUsuario, Long idEstablecimiento) {
        Establecimiento actual = exigirEstablecimiento(idEstablecimiento);
        exigirPropietario(idEstablecimiento, idUsuario,
                "Solo el propietario puede consultar este establecimiento.");
        return actual;
    }

    @Override
    public List<Establecimiento> listarEstablecimientosPropios(Long idUsuario) {
        return repositorio.listarEstablecimientosDeUsuario(idUsuario);
    }

    @Override
    @Transactional
    public Establecimiento actualizarEstablecimiento(Long idUsuario, Long idEstablecimiento,
                                                     ActualizarEstablecimientoCommand command) {
        Establecimiento actual = exigirEstablecimiento(idEstablecimiento);
        exigirPropietario(idEstablecimiento, idUsuario,
                "Solo el propietario puede editar este establecimiento.");
        // El agregado valida nombre, direccion, distrito y estado.
        Establecimiento actualizado = new Establecimiento(actual.id(), actual.idPropietario(),
                command.idDistrito(), command.nombre(), command.direccion(), command.referencia(),
                actual.estado());
        repositorio.actualizarEstablecimiento(actualizado.id(), actualizado.idDistrito(),
                actualizado.nombre(), actualizado.direccion(), actualizado.referencia());
        return actualizado;
    }

    @Override
    @Transactional
    public Cancha actualizarCancha(Long idUsuario, Long idCancha, ActualizarCanchaCommand command) {
        Cancha actual = exigirCancha(idCancha);
        if (!repositorio.esPropietarioDeCancha(idCancha, idUsuario)) {
            throw new DomainException(ErrorCodes.SIN_PERMISO,
                    "Solo el propietario puede editar esta cancha.");
        }
        Cancha actualizada = new Cancha(actual.id(), actual.idEstablecimiento(), command.nombre(),
                command.capacidad(), command.precioBase(), actual.estado());
        if (repositorio.existeOtraCanchaConNombre(actual.idEstablecimiento(), actualizada.nombre(),
                actualizada.id())) {
            throw new DomainException(ErrorCodes.CONFLICTO,
                    "Ya existe otra cancha con ese nombre en el establecimiento.");
        }
        repositorio.actualizarCancha(idCancha, actualizada.nombre(), command.descripcion(),
                actualizada.capacidad(), actualizada.precioBase());
        return actualizada;
    }

    @Override
    @Transactional
    public void darDeBajaEstablecimiento(Long idUsuario, Long idEstablecimiento) {
        exigirEstablecimiento(idEstablecimiento);
        exigirPropietario(idEstablecimiento, idUsuario,
                "Solo el propietario puede dar de baja este establecimiento.");
        if (repositorio.tieneReservasVigentesFuturasDeEstablecimiento(idEstablecimiento)) {
            throw new DomainException(ErrorCodes.CONFLICTO,
                    "El establecimiento tiene reservas vigentes; atiendalas antes de darlo de baja.");
        }
        if (!repositorio.desactivarEstablecimiento(idEstablecimiento)) {
            throw new DomainException(ErrorCodes.NO_ENCONTRADO,
                    "El establecimiento no existe o ya estaba inactivo.");
        }
        // Las canchas dejan de ofrecerse, pero sus reservas historicas se conservan.
        repositorio.desactivarCanchasDeEstablecimiento(idEstablecimiento);
    }

    @Override
    @Transactional
    public void darDeBajaCancha(Long idUsuario, Long idCancha) {
        exigirCancha(idCancha);
        if (!repositorio.esPropietarioDeCancha(idCancha, idUsuario)) {
            throw new DomainException(ErrorCodes.SIN_PERMISO,
                    "Solo el propietario puede dar de baja esta cancha.");
        }
        if (repositorio.tieneReservasVigentesFuturas(idCancha)) {
            throw new DomainException(ErrorCodes.CONFLICTO,
                    "La cancha tiene reservas vigentes; atiendelas antes de darla de baja.");
        }
        if (!repositorio.desactivarCancha(idCancha)) {
            throw new DomainException(ErrorCodes.NO_ENCONTRADO,
                    "La cancha no existe o ya estaba inactiva.");
        }
    }

    private Establecimiento exigirEstablecimiento(Long idEstablecimiento) {
        if (idEstablecimiento == null) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "El establecimiento es obligatorio.");
        }
        return repositorio.buscarEstablecimiento(idEstablecimiento)
                .orElseThrow(() -> new DomainException(ErrorCodes.NO_ENCONTRADO,
                        "El establecimiento no existe."));
    }

    private Cancha exigirCancha(Long idCancha) {
        if (idCancha == null) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "La cancha es obligatoria.");
        }
        return repositorio.buscarCancha(idCancha)
                .orElseThrow(() -> new DomainException(ErrorCodes.NO_ENCONTRADO,
                        "La cancha no existe."));
    }

    private void exigirPropietario(Long idEstablecimiento, Long idUsuario, String mensaje) {
        if (!repositorio.esPropietarioDe(idEstablecimiento, idUsuario)) {
            throw new DomainException(ErrorCodes.SIN_PERMISO, mensaje);
        }
    }

    @Override
    @Transactional
    public Long registrarCochera(Long idUsuario, RegistrarCocheraCommand command) {
        if (command.idEstablecimiento() == null) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "El establecimiento es obligatorio.");
        }
        if (!repositorio.esPropietarioDe(command.idEstablecimiento(), idUsuario)) {
            throw new DomainException(ErrorCodes.SIN_PERMISO,
                    "Solo el propietario puede registrar cocheras en este establecimiento.");
        }
        // La construccion del agregado aplica las invariantes (nombre, capacidad, tarifa).
        Cochera validada = new Cochera(null, command.idEstablecimiento(), command.nombre(),
                command.capacidad(), command.tarifaHora(), Cochera.Estado.ACTIVA);
        return repositorio.crearCochera(validada);
    }

    @Override
    public List<Cochera> listarCocheras(Long idUsuario, Long idEstablecimiento) {
        if (idEstablecimiento == null) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "El establecimiento es obligatorio.");
        }
        if (!repositorio.esPropietarioDe(idEstablecimiento, idUsuario)) {
            throw new DomainException(ErrorCodes.SIN_PERMISO,
                    "Solo el propietario puede consultar las cocheras de este establecimiento.");
        }
        return repositorio.listarCocheras(idEstablecimiento);
    }

    @Override
    @Transactional
    public void desactivarCochera(Long idUsuario, Long idEstablecimiento, Long idCochera) {
        if (idEstablecimiento == null) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "El establecimiento es obligatorio.");
        }
        if (!repositorio.esPropietarioDe(idEstablecimiento, idUsuario)) {
            throw new DomainException(ErrorCodes.SIN_PERMISO,
                    "Solo el propietario puede gestionar las cocheras de este establecimiento.");
        }
        // Baja logica: la cochera deja de ofrecerse, pero sus reservas historicas se conservan.
        if (!repositorio.desactivarCochera(idEstablecimiento, idCochera)) {
            throw new DomainException(ErrorCodes.NO_ENCONTRADO,
                    "La cochera no existe o ya estaba inactiva en este establecimiento.");
        }
    }
}
