package pe.com.canchadirecta.bookings.application.port.in;

import pe.com.canchadirecta.bookings.application.dto.CrearReservaCommand;
import pe.com.canchadirecta.bookings.domain.model.Reserva;

import java.time.Instant;
import java.util.List;

/** Casos de uso de reservas. */
public interface ReservasUseCase {

    /** Crea una reserva o falla con `SLOT_UNAVAILABLE` si la franja ya esta ocupada. */
    Reserva crear(CrearReservaCommand command);

    /**
     * Reprograma una reserva propia vigente conservando su duracion e importes (CU03.7).
     * Falla con `409` si la nueva franja esta ocupada y con `403` si es de otro titular.
     */
    Reserva modificar(Long idUsuario, Long idReserva, Instant inicio, Instant fin);

    List<Reserva> listarMias(Long idUsuario, int limite);
}
