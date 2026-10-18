package pe.com.canchadirecta.reception.application.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/** Alta de reserva presencial desde recepcion. */
public record ReservaPresencialCommand(
        Long idEstablecimiento,
        Long idCancha,
        Long idCliente,
        LocalDate fecha,
        LocalTime horaInicio,
        int duracionMinutos,
        boolean pagoRecibido,
        String codigoOperacion) {
}
