package pe.com.canchadirecta.reception.adapter.in.rest.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/** Alta de reserva presencial desde recepcion. */
public record NuevaReservaPresencialRequest(
        Long idEstablecimiento,
        Long idCancha,
        Long idCliente,
        LocalDate fecha,
        LocalTime horaInicio,
        Integer duracionMinutos,
        boolean pagoRecibido,
        String codigoOperacion) {
}
