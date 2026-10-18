package pe.com.canchadirecta.bookings.application.port.in;

import pe.com.canchadirecta.bookings.application.dto.DisponibilidadDia;

import java.time.LocalDate;

/** Consulta de franjas libres de una cancha para una fecha local y duracion dadas. */
public interface ConsultarDisponibilidadUseCase {

    DisponibilidadDia consultar(Long idCancha, LocalDate fecha, long duracionMinutos);
}
