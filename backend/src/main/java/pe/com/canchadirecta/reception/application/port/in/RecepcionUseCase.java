package pe.com.canchadirecta.reception.application.port.in;

import pe.com.canchadirecta.reception.application.dto.ClienteRecepcion;
import pe.com.canchadirecta.reception.application.dto.CanchaRecepcion;
import pe.com.canchadirecta.reception.application.dto.EstablecimientoRecepcion;
import pe.com.canchadirecta.reception.application.dto.IncidenciaRecepcion;
import pe.com.canchadirecta.reception.application.dto.ReservaPresencialCommand;
import pe.com.canchadirecta.reception.application.dto.ReservaRecepcion;
import pe.com.canchadirecta.reception.application.dto.ResumenTurno;

import java.time.LocalDate;
import java.util.List;

/** Casos de uso del portal de recepcion (Bloque D, ADR-0038). */
public interface RecepcionUseCase {

    List<EstablecimientoRecepcion> establecimientos(Long idUsuario);

    /** Canchas activas de las sedes accesibles (reserva presencial). */
    List<CanchaRecepcion> canchas(Long idUsuario, Long idEstablecimiento);

    ResumenTurno resumen(Long idUsuario, Long idEstablecimiento, LocalDate fecha);

    List<ReservaRecepcion> agenda(Long idUsuario, Long idEstablecimiento, LocalDate fecha);

    List<ReservaRecepcion> reservas(Long idUsuario, Long idEstablecimiento, String estado, int limite);

    List<ClienteRecepcion> clientes(String texto);

    ReservaRecepcion reservaPresencial(Long idUsuario, ReservaPresencialCommand command);

    ReservaRecepcion checkIn(Long idUsuario, Long idReserva);

    ReservaRecepcion cobrar(Long idUsuario, Long idReserva, String medioPago, String codigoOperacion);

    List<IncidenciaRecepcion> incidencias(Long idUsuario, int limite);

    IncidenciaRecepcion registrarIncidencia(Long idUsuario, Long idReserva, String asunto, String descripcion);
}
