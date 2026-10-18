package pe.com.canchadirecta.bookings.application.port.in;

import pe.com.canchadirecta.bookings.application.dto.ReservaRecibida;
import pe.com.canchadirecta.bookings.domain.model.Reserva;

import java.time.Instant;
import java.util.List;

/**
 * Atencion de las reservas recibidas por el propietario de la cancha (HU-008-2).
 *
 * <p>Aceptar o rechazar una solicitud es una decision del propietario, no del titular:
 * el identificador del usuario sale siempre del token y ninguna operacion puede
 * alcanzar reservas de canchas ajenas (403).</p>
 */
public interface AtenderReservaUseCase {

    /**
     * Reservas recibidas por las canchas del propietario, con el titular y el ultimo pago
     * de cada una (ADR-0044); `estado` nulo lista todas y `idCancha`/`desde`/`hasta` nulos
     * no filtran (el rango se aplica por solape).
     */
    List<ReservaRecibida> recibidas(Long idUsuario, Reserva.Estado estado, Long idCancha,
                                    Instant desde, Instant hasta, int limite);

    /** Pasa una reserva pendiente a `CONFIRMADA`. */
    Reserva confirmar(Long idReserva, Long idUsuario);

    /** Pasa una reserva pendiente a `RECHAZADA` y libera la franja. */
    Reserva rechazar(Long idReserva, Long idUsuario, String motivo);

    /** Confirmacion desde administracion, sin exigir propiedad de la cancha. */
    Reserva confirmarComoAdmin(Long idReserva);

    /** Rechazo desde administracion, sin exigir propiedad y con la misma politica de liberacion. */
    Reserva rechazarComoAdmin(Long idReserva, String motivo);

    /**
     * Revoca una reserva ya `CONFIRMADA` que aun no ha comenzado (L-14): pasa a
     * `RECHAZADA` y libera la franja, con reembolso total porque la responsabilidad es
     * del propietario.
     */
    Reserva revocar(Long idReserva, Long idUsuario, String motivo);
}
