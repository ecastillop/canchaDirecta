package pe.com.canchadirecta.reception.application.port.out;

import pe.com.canchadirecta.reception.application.dto.ClienteRecepcion;
import pe.com.canchadirecta.reception.application.dto.CanchaRecepcion;
import pe.com.canchadirecta.reception.application.dto.EstablecimientoRecepcion;
import pe.com.canchadirecta.reception.application.dto.IncidenciaRecepcion;
import pe.com.canchadirecta.reception.application.dto.OperadorAsignado;
import pe.com.canchadirecta.reception.application.dto.ReservaRecepcion;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Puerto de salida de recepcion (SQL nativo).
 *
 * <p>Todas las consultas de reservas filtran por los establecimientos accesibles al usuario:
 * un `OPERADOR` ve solo sus sedes asignadas y activas; un `OWNER`, las propias; un
 * `PLATFORM_ADMIN`, todas (ADR-0038).</p>
 */
public interface RecepcionRepositorio {

    /** Sedes accesibles para el usuario segun su rol y asignaciones activas. */
    List<EstablecimientoRecepcion> establecimientosAccesibles(Long idUsuario);

    boolean puedeOperarEstablecimiento(Long idUsuario, Long idEstablecimiento);

    boolean puedeOperarCancha(Long idUsuario, Long idCancha);

    boolean puedeOperarReserva(Long idUsuario, Long idReserva);

    /** Reservas de las sedes accesibles que inician dentro de `[desde, hasta)`; sede opcional. */
    List<ReservaRecepcion> reservasEntre(Long idUsuario, Long idEstablecimiento, Instant desde, Instant hasta,
                                         int limite);

    /** Reservas de las sedes accesibles, opcionalmente filtradas por estado; mas recientes primero. */
    List<ReservaRecepcion> listarReservas(Long idUsuario, Long idEstablecimiento, String estado, int limite);

    Optional<ReservaRecepcion> reserva(Long idUsuario, Long idReserva);

    long contarReservasEntre(Long idUsuario, Long idEstablecimiento, Instant desde, Instant hasta);

    /** Reservas en un estado concreto que inician dentro de `[desde, hasta)`. */
    long contarEstadoEntre(Long idUsuario, Long idEstablecimiento, String estado, Instant desde, Instant hasta);

    /** Reservas vigentes del dia sin pago aprobado. */
    long contarCobrosPendientes(Long idUsuario, Long idEstablecimiento, Instant desde, Instant hasta);

    /** Canchas activas de las sedes accesibles. */
    long contarCanchasActivas(Long idUsuario, Long idEstablecimiento);

    /** Canchas activas de las sedes accesibles, para la reserva presencial. */
    List<CanchaRecepcion> canchasAccesibles(Long idUsuario, Long idEstablecimiento);

    /** Clientes registrados que coinciden por nombre, correo o telefono (reserva presencial). */
    List<ClienteRecepcion> buscarClientes(String texto, int limite);

    Long registrarIncidencia(Long idUsuario, Long idReserva, String asunto, String descripcion);

    List<IncidenciaRecepcion> incidenciasDe(Long idUsuario, int limite);

    boolean establecimientoExiste(Long idEstablecimiento);

    boolean usuarioExiste(Long idUsuario);

    List<OperadorAsignado> operadoresDe(Long idEstablecimiento);

    /** Vincula al usuario con la sede y asegura que tenga el rol OPERADOR. */
    void asignarOperador(Long idEstablecimiento, Long idUsuario, String cargo);

    void removerOperador(Long idEstablecimiento, Long idUsuario);
}
