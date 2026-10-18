package pe.com.canchadirecta.bookings.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.com.canchadirecta.bookings.application.port.in.AtenderReservaUseCase;
import pe.com.canchadirecta.bookings.application.port.out.ReservaRepositorio;
import pe.com.canchadirecta.bookings.domain.model.Reserva;
import pe.com.canchadirecta.shared.application.port.out.AvisosUsuario;
import pe.com.canchadirecta.shared.application.port.out.PoliticaReembolso;
import pe.com.canchadirecta.shared.application.port.out.Reloj;
import pe.com.canchadirecta.shared.domain.DomainException;
import pe.com.canchadirecta.shared.domain.ErrorCodes;

import java.util.List;

/**
 * Atencion de reservas por el propietario (HU-008-2).
 *
 * <p>Ambas decisiones siguen el mismo patron que la cancelacion: la reserva se lee con
 * bloqueo exclusivo de su fila (`FOR UPDATE`), de modo que dos decisiones simultaneas
 * sobre la misma reserva no pueden registrarse dos veces: la segunda ve el estado ya
 * cambiado y recibe 409.</p>
 *
 * <p>Solo se atienden reservas `PENDIENTE` y que no hayan comenzado. Al pasar a
 * `RECHAZADA` la franja deja de retener cupo, por lo que reaparece en la disponibilidad
 * (la ocupacion solo cuenta `PENDIENTE` y `CONFIRMADA`, ver {@link Reserva#ocupaFranja}).
 * El rechazo aplica la politica de reembolso del propietario: si la reserva tenia un pago
 * aprobado, se reembolsa el total (L-12).</p>
 */
@ApplicationScoped
public class AtenderReservaService implements AtenderReservaUseCase {

    /** Motivo con el que queda registrada la decision en `historial_reserva`. */
    private static final String MOTIVO_RECHAZO = "Rechazada por el propietario";

    /** Motivo con el que queda registrada la revocacion de una reserva ya confirmada. */
    private static final String MOTIVO_REVOCACION = "Revocada por el propietario";

    private final ReservaRepositorio reservas;
    private final PoliticaReembolso reembolsos;
    private final Reloj reloj;
    private final AvisosUsuario avisos;

    @Inject
    public AtenderReservaService(ReservaRepositorio reservas, PoliticaReembolso reembolsos, Reloj reloj,
                                 AvisosUsuario avisos) {
        this.reservas = reservas;
        this.reembolsos = reembolsos;
        this.reloj = reloj;
        this.avisos = avisos;
    }

    @Override
    public List<Reserva> recibidas(Long idUsuario, Reserva.Estado estado, int limite) {
        return reservas.listarPorPropietario(idUsuario, estado, limite);
    }

    @Override
    @Transactional
    public Reserva confirmar(Long idReserva, Long idUsuario) {
        Reserva pendiente = pendienteAtendible(idReserva, idUsuario, true);
        reservas.cambiarEstado(idReserva, pendiente.estado(), Reserva.Estado.CONFIRMADA,
                "Confirmada por el propietario");
        avisos.avisar(pendiente.idUsuario(), "Reserva confirmada",
                "Tu reserva #" + idReserva + " fue confirmada por el establecimiento.");
        return conEstado(pendiente, Reserva.Estado.CONFIRMADA);
    }

    @Override
    @Transactional
    public Reserva confirmarComoAdmin(Long idReserva) {
        Reserva pendiente = pendienteAtendible(idReserva, null, false);
        reservas.cambiarEstado(idReserva, pendiente.estado(), Reserva.Estado.CONFIRMADA,
                "Confirmada por administracion");
        avisos.avisar(pendiente.idUsuario(), "Reserva confirmada",
                "Tu reserva #" + idReserva + " fue confirmada por la plataforma.");
        return conEstado(pendiente, Reserva.Estado.CONFIRMADA);
    }

    @Override
    @Transactional
    public Reserva rechazarComoAdmin(Long idReserva, String motivo) {
        Reserva pendiente = pendienteAtendible(idReserva, null, false);
        reservas.cambiarEstado(idReserva, pendiente.estado(), Reserva.Estado.RECHAZADA,
                textoRechazo(motivo));
        reembolsos.alLiberarReserva(idReserva, pendiente.franja().inicio(), reloj.ahora(), true);
        avisos.avisar(pendiente.idUsuario(), "Reserva rechazada",
                "Tu reserva #" + idReserva + " fue rechazada por la plataforma.");
        return conEstado(pendiente, Reserva.Estado.RECHAZADA);
    }

    @Override
    @Transactional
    public Reserva rechazar(Long idReserva, Long idUsuario, String motivo) {
        Reserva pendiente = pendienteAtendible(idReserva, idUsuario, true);
        reservas.cambiarEstado(idReserva, pendiente.estado(), Reserva.Estado.RECHAZADA, textoRechazo(motivo));
        reembolsos.alLiberarReserva(idReserva, pendiente.franja().inicio(), reloj.ahora(), true);
        avisos.avisar(pendiente.idUsuario(), "Reserva rechazada",
                "Tu reserva #" + idReserva + " fue rechazada por el establecimiento.");
        return conEstado(pendiente, Reserva.Estado.RECHAZADA);
    }

    @Override
    @Transactional
    public Reserva revocar(Long idReserva, Long idUsuario, String motivo) {
        if (idReserva == null) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "La reserva es obligatoria.");
        }
        Reserva confirmada = reservas.buscarParaActualizar(idReserva)
                .orElseThrow(() -> new DomainException(ErrorCodes.NO_ENCONTRADO, "La reserva no existe."));

        if (!reservas.esPropietarioDeReserva(idReserva, idUsuario)) {
            throw new DomainException(ErrorCodes.SIN_PERMISO,
                    "Solo el propietario de la cancha puede revocar sus reservas.");
        }
        if (confirmada.estado() != Reserva.Estado.CONFIRMADA) {
            throw new DomainException(ErrorCodes.CONFLICTO,
                    "Solo se puede revocar una reserva confirmada; su estado es "
                            + confirmada.estado() + ".");
        }
        if (!confirmada.franja().inicio().isAfter(reloj.ahora())) {
            throw new DomainException(ErrorCodes.CONFLICTO,
                    "La reserva ya comenzo y no se puede revocar.");
        }

        reservas.cambiarEstado(idReserva, Reserva.Estado.CONFIRMADA, Reserva.Estado.RECHAZADA,
                textoRevocacion(motivo));
        reembolsos.alLiberarReserva(idReserva, confirmada.franja().inicio(), reloj.ahora(), true);
        avisos.avisar(confirmada.idUsuario(), "Reserva revocada",
                "El establecimiento revoco tu reserva #" + idReserva + ".");
        return conEstado(confirmada, Reserva.Estado.RECHAZADA);
    }

    /** Reglas comunes de aceptacion y rechazo: existencia, propiedad, estado y tiempo. */
    private Reserva pendienteAtendible(Long idReserva, Long idUsuario, boolean exigirPropietario) {
        if (idReserva == null) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "La reserva es obligatoria.");
        }
        Reserva reserva = reservas.buscarParaActualizar(idReserva)
                .orElseThrow(() -> new DomainException(ErrorCodes.NO_ENCONTRADO, "La reserva no existe."));

        if (exigirPropietario && !reservas.esPropietarioDeReserva(idReserva, idUsuario)) {
            throw new DomainException(ErrorCodes.SIN_PERMISO,
                    "Solo el propietario de la cancha puede atender sus reservas.");
        }
        if (reserva.estado() != Reserva.Estado.PENDIENTE) {
            throw new DomainException(ErrorCodes.CONFLICTO,
                    "Solo se puede aceptar o rechazar una reserva pendiente; su estado es "
                            + reserva.estado() + ".");
        }
        if (!reserva.franja().inicio().isAfter(reloj.ahora())) {
            throw new DomainException(ErrorCodes.CONFLICTO,
                    "La reserva ya comenzo y no se puede atender.");
        }
        return reserva;
    }

    /** `historial_reserva.observacion` es VARCHAR(250): prefijo + motivo acotado en el DTO. */
    private static String textoRechazo(String motivo) {
        if (motivo == null || motivo.isBlank()) {
            return MOTIVO_RECHAZO;
        }
        return MOTIVO_RECHAZO + ": " + motivo.trim();
    }

    /** Mismo criterio que el rechazo: prefijo fijo + motivo acotado en el DTO. */
    private static String textoRevocacion(String motivo) {
        if (motivo == null || motivo.isBlank()) {
            return MOTIVO_REVOCACION;
        }
        return MOTIVO_REVOCACION + ": " + motivo.trim();
    }

    private static Reserva conEstado(Reserva reserva, Reserva.Estado nuevo) {
        return new Reserva(reserva.id(), reserva.idUsuario(), reserva.idCancha(), reserva.franja(),
                reserva.montoCancha(), reserva.montoDescuento(), nuevo, reserva.fechaCreacion(),
                reserva.cochera());
    }
}
