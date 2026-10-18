package pe.com.canchadirecta.engagement.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.com.canchadirecta.bookings.application.port.out.ReservaRepositorio;
import pe.com.canchadirecta.bookings.domain.model.Reserva;
import pe.com.canchadirecta.engagement.application.port.in.CalificarReservaUseCase;
import pe.com.canchadirecta.engagement.application.port.out.CalificacionRepositorio;
import pe.com.canchadirecta.engagement.domain.model.Calificacion;
import pe.com.canchadirecta.shared.application.port.out.Reloj;
import pe.com.canchadirecta.shared.domain.DomainException;
import pe.com.canchadirecta.shared.domain.ErrorCodes;

/**
 * Calificacion de una reserva finalizada (CU03.8, RN-04).
 *
 * <p>Reglas:</p>
 * <ul>
 *   <li>Solo el titular de la reserva puede calificarla.</li>
 *   <li>Solo una reserva `FINALIZADA` (ya usada) es calificable; su cierre lo hace el job de
 *       finalizacion (ADR-0023).</li>
 *   <li>Una sola calificacion por reserva (RN-04); si ya existe, `409`.</li>
 * </ul>
 */
@ApplicationScoped
public class CalificarReservaService implements CalificarReservaUseCase {

    private final ReservaRepositorio reservas;
    private final CalificacionRepositorio calificaciones;
    private final Reloj reloj;

    @Inject
    public CalificarReservaService(ReservaRepositorio reservas, CalificacionRepositorio calificaciones,
                                   Reloj reloj) {
        this.reservas = reservas;
        this.calificaciones = calificaciones;
        this.reloj = reloj;
    }

    @Override
    @Transactional
    public Calificacion calificar(Long idReserva, Long idUsuario, int puntaje, String comentario) {
        Reserva reserva = reservas.buscar(idReserva)
                .orElseThrow(() -> new DomainException(ErrorCodes.NO_ENCONTRADO, "La reserva no existe."));
        if (!reserva.idUsuario().equals(idUsuario)) {
            throw new DomainException(ErrorCodes.SIN_PERMISO, "Solo el titular puede calificar su reserva.");
        }
        if (reserva.estado() != Reserva.Estado.FINALIZADA) {
            throw new DomainException(ErrorCodes.CONFLICTO,
                    "Solo se puede calificar una reserva finalizada.");
        }
        if (calificaciones.existeParaReserva(idReserva)) {
            throw new DomainException(ErrorCodes.CONFLICTO, "La reserva ya tiene una calificacion.");
        }
        return calificaciones.registrar(new Calificacion(null, idReserva, puntaje, comentario, reloj.ahora()));
    }
}
