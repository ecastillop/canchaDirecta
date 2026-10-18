package pe.com.canchadirecta.bookings.application.port.in;

import pe.com.canchadirecta.bookings.domain.model.Reserva;

/** Cancelacion de una reserva propia. Devuelve la reserva ya cancelada. */
public interface CancelarReservaUseCase {

    Reserva cancelar(Long idReserva, Long idUsuario);
}
