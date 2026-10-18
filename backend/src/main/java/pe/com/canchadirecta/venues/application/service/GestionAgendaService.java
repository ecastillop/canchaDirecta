package pe.com.canchadirecta.venues.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.com.canchadirecta.shared.application.port.out.Reloj;
import pe.com.canchadirecta.shared.domain.DomainException;
import pe.com.canchadirecta.shared.domain.ErrorCodes;
import pe.com.canchadirecta.venues.application.dto.RegistrarBloqueoCommand;
import pe.com.canchadirecta.venues.application.dto.RegistrarHorarioCommand;
import pe.com.canchadirecta.venues.application.port.in.GestionAgendaUseCase;
import pe.com.canchadirecta.venues.application.port.out.VenuesRepositorio;
import pe.com.canchadirecta.venues.domain.model.BloqueoCancha;
import pe.com.canchadirecta.venues.domain.model.HorarioCancha;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Agenda de la cancha: horarios semanales y bloqueos operativos.
 *
 * <p><b>Solapes.</b> Los horarios no pueden solaparse entre si dentro del mismo dia
 * (si no, la disponibilidad ofreceria dos precios para la misma hora). Los bloqueos no
 * pueden solaparse con otra reserva vigente ni con otro bloqueo: la comprobacion se hace
 * bajo el bloqueo exclusivo de la fila de la cancha, el mismo que usa RN-01, para que no
 * pueda colarse una reserva entre la comprobacion y el INSERT (ADR-0017).</p>
 */
@ApplicationScoped
public class GestionAgendaService implements GestionAgendaUseCase {

    /** Dias que se listan por defecto cuando no se pide un rango de bloqueos. */
    private static final long DIAS_POR_DEFECTO = 30;

    private final VenuesRepositorio repositorio;
    private final Reloj reloj;

    @Inject
    public GestionAgendaService(VenuesRepositorio repositorio, Reloj reloj) {
        this.repositorio = repositorio;
        this.reloj = reloj;
    }

    @Override
    public List<HorarioCancha> listarHorarios(Long idCancha) {
        exigirCancha(idCancha);
        return repositorio.listarHorarios(idCancha);
    }

    @Override
    @Transactional
    public Long registrarHorario(Long idUsuario, RegistrarHorarioCommand command) {
        exigirCancha(command.idCancha());
        exigirPropietario(command.idCancha(), idUsuario);

        // La construccion aplica las invariantes (dia, rango, precio).
        HorarioCancha nuevo = new HorarioCancha(null, command.idCancha(), command.diaSemana(),
                command.inicio(), command.fin(), command.precio(), true);
        boolean solapa = repositorio.listarHorarios(command.idCancha()).stream()
                .anyMatch(existente -> existente.seSolapaCon(nuevo));
        if (solapa) {
            throw new DomainException(ErrorCodes.CONFLICTO,
                    "Ese rango se solapa con otro horario activo de la misma cancha.");
        }

        return repositorio.crearHorario(nuevo);
    }

    @Override
    @Transactional
    public void eliminarHorario(Long idUsuario, Long idCancha, Long idHorario) {
        exigirCancha(idCancha);
        exigirPropietario(idCancha, idUsuario);
        // Baja logica: la columna `activo` existe en el esquema y la disponibilidad ya la filtra.
        if (!repositorio.desactivarHorario(idCancha, idHorario)) {
            throw new DomainException(ErrorCodes.NO_ENCONTRADO, "El horario no existe en esta cancha.");
        }
    }

    @Override
    public List<BloqueoCancha> listarBloqueos(Long idUsuario, Long idCancha, Instant desde, Instant hasta) {
        exigirCancha(idCancha);
        exigirPropietario(idCancha, idUsuario);

        Instant inicio = desde != null ? desde : reloj.ahora();
        Instant fin = hasta != null ? hasta : inicio.plus(DIAS_POR_DEFECTO, ChronoUnit.DAYS);
        if (!inicio.isBefore(fin)) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS,
                    "El rango de consulta debe tener un inicio anterior al fin.");
        }
        return repositorio.listarBloqueos(idCancha, inicio, fin);
    }

    @Override
    @Transactional
    public Long registrarBloqueo(Long idUsuario, RegistrarBloqueoCommand command) {
        // La construccion aplica las invariantes (rango y motivo) antes de tocar la base.
        BloqueoCancha bloqueo = new BloqueoCancha(null, command.idCancha(), command.inicio(),
                command.fin(), command.motivo());
        exigirCancha(bloqueo.idCancha());
        exigirPropietario(bloqueo.idCancha(), idUsuario);

        // Mismo orden de bloqueo que RN-01: primero la fila de la cancha.
        repositorio.bloquearCancha(bloqueo.idCancha())
                .orElseThrow(() -> new DomainException(ErrorCodes.NO_ENCONTRADO, "La cancha no existe."));

        if (repositorio.existeReservaVigenteSolapada(bloqueo.idCancha(), bloqueo.inicio(), bloqueo.fin())) {
            throw new DomainException(ErrorCodes.CONFLICTO,
                    "No se puede bloquear una franja que ya tiene una reserva vigente.");
        }
        if (repositorio.existeBloqueoSolapado(bloqueo.idCancha(), bloqueo.inicio(), bloqueo.fin())) {
            throw new DomainException(ErrorCodes.CONFLICTO, "Ese rango ya esta bloqueado.");
        }

        return repositorio.crearBloqueo(bloqueo);
    }

    @Override
    @Transactional
    public void eliminarBloqueo(Long idUsuario, Long idCancha, Long idBloqueo) {
        exigirCancha(idCancha);
        exigirPropietario(idCancha, idUsuario);
        if (!repositorio.eliminarBloqueo(idCancha, idBloqueo)) {
            throw new DomainException(ErrorCodes.NO_ENCONTRADO, "El bloqueo no existe en esta cancha.");
        }
    }

    private void exigirCancha(Long idCancha) {
        if (idCancha == null) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "La cancha es obligatoria.");
        }
        if (repositorio.buscarCancha(idCancha).isEmpty()) {
            throw new DomainException(ErrorCodes.NO_ENCONTRADO, "La cancha no existe.");
        }
    }

    private void exigirPropietario(Long idCancha, Long idUsuario) {
        if (idUsuario == null || !repositorio.esPropietarioDeCancha(idCancha, idUsuario)) {
            throw new DomainException(ErrorCodes.SIN_PERMISO,
                    "Solo el propietario puede gestionar la agenda de esta cancha.");
        }
    }
}
