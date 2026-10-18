package pe.com.canchadirecta.bookings.application.port.in;

import pe.com.canchadirecta.bookings.domain.model.Reserva;

import java.util.List;

/**
 * Atencion de las reservas recibidas por el propietario de la cancha (HU-008-2).
 *
 * <p>Aceptar o rechazar una solicitud es una decision del propietario, no del titular:
 * el identificador del usuario sale siempre del token y ninguna operacion puede
 * alcanzar reservas de canchas ajenas (403).</p>
 */
public interface AtenderReservaUseCase {

    /** Reservas recibidas por las canchas del propietario; `estado` nulo lista todas. */
    List<Reserva> recibidas(Long idUsuario, Reserva.Estado estado, int limite);

    /** Pasa una reserva pendiente a `CONFIRMADA`. */
    Reserva confirmar(Long idReserva, Long idUsuario);

    /** Pasa una reserva pendiente a `RECHAZADA` y libera la franja. */
    Reserva rechazar(Long idReserva, Long idUsuario, String motivo);

    /**
     * Revoca una reserva ya `CONFIRMADA` que aun no ha comenzado (L-14): pasa a
     * `RECHAZADA` y libera la franja, con reembolso total porque la responsabilidad es
     * del propietario.
     */
    Reserva revocar(Long idReserva, Long idUsuario, String motivo);
}
