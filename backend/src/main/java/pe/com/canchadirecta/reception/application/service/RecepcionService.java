package pe.com.canchadirecta.reception.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import pe.com.canchadirecta.bookings.application.dto.CrearReservaCommand;
import pe.com.canchadirecta.bookings.application.port.in.ReservasUseCase;
import pe.com.canchadirecta.bookings.application.port.out.ReservaRepositorio;
import pe.com.canchadirecta.bookings.domain.model.Reserva;
import pe.com.canchadirecta.payments.application.port.out.PagoRepositorio;
import pe.com.canchadirecta.payments.domain.model.Pago;
import pe.com.canchadirecta.reception.application.dto.ClienteRecepcion;
import pe.com.canchadirecta.reception.application.dto.CanchaRecepcion;
import pe.com.canchadirecta.reception.application.dto.EstablecimientoRecepcion;
import pe.com.canchadirecta.reception.application.dto.IncidenciaRecepcion;
import pe.com.canchadirecta.reception.application.dto.ReservaPresencialCommand;
import pe.com.canchadirecta.reception.application.dto.ReservaRecepcion;
import pe.com.canchadirecta.reception.application.dto.ResumenTurno;
import pe.com.canchadirecta.reception.application.port.in.RecepcionUseCase;
import pe.com.canchadirecta.reception.application.port.out.RecepcionRepositorio;
import pe.com.canchadirecta.shared.application.port.out.AvisosUsuario;
import pe.com.canchadirecta.shared.application.port.out.Reloj;
import pe.com.canchadirecta.shared.domain.DomainException;
import pe.com.canchadirecta.shared.domain.ErrorCodes;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

/**
 * Portal de recepcion (Bloque D, ADR-0038).
 *
 * <p>El alcance lo define {@link RecepcionRepositorio}: el operador solo actua sobre las sedes
 * que tiene asignadas y activas; el propietario sobre las suyas; el administrador sobre todas.
 * El check-in cierra la reserva (`CONFIRMADA` -> `FINALIZADA`) y el cobro registra el pago
 * aprobado en efectivo a traves del puerto de pagos. Un walk-in cobrado en mostrador se confirma
 * de inmediato: el cliente esta presente y el personal recibio el pago (L-24).</p>
 */
@ApplicationScoped
public class RecepcionService implements RecepcionUseCase {

    private static final String MOTIVO_CHECKIN = "Check-in en recepcion";
    private static final String MOTIVO_CONFIRMACION = "Confirmada en recepcion con el pago";
    private static final Duration VENTANA_CHECKIN = Duration.ofHours(2);
    private static final List<String> MEDIOS = List.of("EFECTIVO", "YAPE", "PLIN", "TARJETA", "POS");

    private final RecepcionRepositorio recepcion;
    private final ReservasUseCase reservas;
    private final ReservaRepositorio reservaRepositorio;
    private final PagoRepositorio pagos;
    private final Reloj reloj;
    private final AvisosUsuario avisos;
    private final ZoneId zona;

    @Inject
    public RecepcionService(RecepcionRepositorio recepcion, ReservasUseCase reservas,
                            ReservaRepositorio reservaRepositorio, PagoRepositorio pagos, Reloj reloj,
                            AvisosUsuario avisos,
                            @ConfigProperty(name = "cd.zona-horaria", defaultValue = "America/Lima")
                            String zonaHoraria) {
        this.recepcion = recepcion;
        this.reservas = reservas;
        this.reservaRepositorio = reservaRepositorio;
        this.pagos = pagos;
        this.reloj = reloj;
        this.avisos = avisos;
        this.zona = ZoneId.of(zonaHoraria);
    }

    @Override
    public List<EstablecimientoRecepcion> establecimientos(Long idUsuario) {
        return recepcion.establecimientosAccesibles(idUsuario);
    }

    @Override
    public List<CanchaRecepcion> canchas(Long idUsuario, Long idEstablecimiento) {
        exigirAcceso(idUsuario, idEstablecimiento);
        return recepcion.canchasAccesibles(idUsuario, idEstablecimiento);
    }

    @Override
    public ResumenTurno resumen(Long idUsuario, Long idEstablecimiento, LocalDate fecha) {
        exigirAcceso(idUsuario, idEstablecimiento);
        Instant desde = inicioDia(fecha);
        Instant hasta = desde.plus(Duration.ofDays(1));
        return new ResumenTurno(
                recepcion.contarReservasEntre(idUsuario, idEstablecimiento, desde, hasta),
                recepcion.contarEstadoEntre(idUsuario, idEstablecimiento, Reserva.Estado.FINALIZADA.name(),
                        desde, hasta),
                recepcion.contarCobrosPendientes(idUsuario, idEstablecimiento, desde, hasta),
                recepcion.contarCanchasActivas(idUsuario, idEstablecimiento));
    }

    @Override
    public List<ReservaRecepcion> agenda(Long idUsuario, Long idEstablecimiento, LocalDate fecha) {
        exigirAcceso(idUsuario, idEstablecimiento);
        Instant desde = inicioDia(fecha);
        return recepcion.reservasEntre(idUsuario, idEstablecimiento, desde, desde.plus(Duration.ofDays(1)), 200);
    }

    @Override
    public List<ReservaRecepcion> reservas(Long idUsuario, Long idEstablecimiento, String estado, int limite) {
        exigirAcceso(idUsuario, idEstablecimiento);
        String filtro = estado == null || estado.isBlank() ? null : estado.trim().toUpperCase(Locale.ROOT);
        if (filtro != null && !esEstadoValido(filtro)) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "Estado de reserva desconocido: " + estado + ".");
        }
        return recepcion.listarReservas(idUsuario, idEstablecimiento, filtro, acotar(limite));
    }

    @Override
    public List<ClienteRecepcion> clientes(String texto) {
        String filtro = texto == null ? "" : texto.trim();
        if (filtro.length() < 2) {
            return List.of();
        }
        return recepcion.buscarClientes(filtro, 10);
    }

    @Override
    @Transactional
    public ReservaRecepcion reservaPresencial(Long idUsuario, ReservaPresencialCommand command) {
        if (command.idCancha() == null) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "La cancha es obligatoria.");
        }
        if (command.idCliente() == null) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS,
                    "Selecciona al cliente registrado para la reserva presencial.");
        }
        if (!recepcion.puedeOperarCancha(idUsuario, command.idCancha())) {
            throw new DomainException(ErrorCodes.SIN_PERMISO, "No operas la sede de esa cancha.");
        }
        if (!recepcion.usuarioExiste(command.idCliente())) {
            throw new DomainException(ErrorCodes.NO_ENCONTRADO, "El cliente no existe.");
        }
        int duracion = command.duracionMinutos() <= 0 ? 60 : command.duracionMinutos();
        Instant inicio = inicioDe(command.fecha(), command.horaInicio());
        Instant fin = inicio.plus(Duration.ofMinutes(duracion));

        Reserva creada = reservas.crear(new CrearReservaCommand(command.idCliente(), command.idCancha(),
                inicio, fin, null, null, null));
        if (command.pagoRecibido()) {
            registrarCobro(creada, "EFECTIVO", command.codigoOperacion());
            confirmarEnMostrador(creada);
        }
        return recepcion.reserva(idUsuario, creada.id())
                .orElseThrow(() -> new DomainException(ErrorCodes.NO_ENCONTRADO,
                        "No se pudo leer la reserva registrada."));
    }

    @Override
    @Transactional
    public ReservaRecepcion checkIn(Long idUsuario, Long idReserva) {
        Reserva reserva = reservaAtendible(idUsuario, idReserva);
        if (reserva.estado() != Reserva.Estado.CONFIRMADA) {
            throw new DomainException(ErrorCodes.CONFLICTO,
                    "Solo se puede hacer check-in de una reserva confirmada; su estado es "
                            + reserva.estado() + ".");
        }
        Instant ahora = reloj.ahora();
        if (ahora.isBefore(reserva.franja().inicio().minus(VENTANA_CHECKIN))) {
            throw new DomainException(ErrorCodes.CONFLICTO,
                    "El check-in se habilita 2 horas antes del inicio de la reserva.");
        }
        if (!ahora.isBefore(reserva.franja().fin())) {
            throw new DomainException(ErrorCodes.CONFLICTO, "La reserva ya termino.");
        }
        reservaRepositorio.cambiarEstado(idReserva, Reserva.Estado.CONFIRMADA, Reserva.Estado.FINALIZADA,
                MOTIVO_CHECKIN);
        avisos.avisar(reserva.idUsuario(), "Check-in registrado",
                "Registramos tu ingreso a la reserva #" + idReserva + ".");
        return recepcion.reserva(idUsuario, idReserva).orElseThrow(() -> new DomainException(
                ErrorCodes.NO_ENCONTRADO, "La reserva no existe."));
    }

    @Override
    @Transactional
    public ReservaRecepcion cobrar(Long idUsuario, Long idReserva, String medioPago, String codigoOperacion) {
        Reserva reserva = reservaAtendible(idUsuario, idReserva);
        if (reserva.estado() != Reserva.Estado.PENDIENTE && reserva.estado() != Reserva.Estado.CONFIRMADA) {
            throw new DomainException(ErrorCodes.CONFLICTO,
                    "Una reserva " + reserva.estado() + " no admite cobro.");
        }
        if (pagos.pagoAprobadoDe(idReserva).isPresent()) {
            throw new DomainException(ErrorCodes.CONFLICTO, "La reserva ya tiene un pago aprobado.");
        }
        registrarCobro(reserva, normalizarMedio(medioPago), codigoOperacion);
        confirmarEnMostrador(reserva);
        return recepcion.reserva(idUsuario, idReserva).orElseThrow(() -> new DomainException(
                ErrorCodes.NO_ENCONTRADO, "La reserva no existe."));
    }

    @Override
    public List<IncidenciaRecepcion> incidencias(Long idUsuario, int limite) {
        return recepcion.incidenciasDe(idUsuario, acotar(limite));
    }

    @Override
    @Transactional
    public IncidenciaRecepcion registrarIncidencia(Long idUsuario, Long idReserva, String asunto,
                                                   String descripcion) {
        if (asunto == null || asunto.isBlank()) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "El asunto es obligatorio.");
        }
        if (asunto.trim().length() > 150) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "El asunto no puede superar 150 caracteres.");
        }
        if (descripcion == null || descripcion.isBlank()) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "La descripcion es obligatoria.");
        }
        if (descripcion.trim().length() > 800) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS,
                    "La descripcion no puede superar 800 caracteres.");
        }
        if (idReserva != null && !recepcion.puedeOperarReserva(idUsuario, idReserva)) {
            throw new DomainException(ErrorCodes.SIN_PERMISO, "No operas la sede de esa reserva.");
        }
        Long id = recepcion.registrarIncidencia(idUsuario, idReserva, asunto.trim(), descripcion.trim());
        return new IncidenciaRecepcion(id, asunto.trim(), descripcion.trim(),
                "ABIERTO", reloj.ahora(), idReserva);
    }

    private void registrarCobro(Reserva reserva, String medio, String codigoOperacion) {
        Instant ahora = reloj.ahora();
        BigDecimal monto = montoTotal(reserva);
        pagos.registrarEvento(reserva.id(), null, "SOLICITADO", "Cobro en recepcion: " + medio);
        Long idPago = pagos.registrarPago(reserva.id(), monto, medio, codigo(codigoOperacion),
                Pago.Estado.APROBADO, ahora);
        pagos.registrarEvento(reserva.id(), idPago, "APROBADO", "Pago recibido en recepcion");
        avisos.avisar(reserva.idUsuario(), "Pago registrado",
                "Recepcion registro el pago de tu reserva #" + reserva.id() + " por " + monto + ".");
    }

    /**
     * En mostrador, cobrar es confirmar: el cliente esta presente y el personal recibio el pago,
     * asi que la reserva no queda esperando al propietario (L-24). Si ya estaba confirmada, no
     * se toca el estado.
     */
    private void confirmarEnMostrador(Reserva reserva) {
        if (reserva.estado() != Reserva.Estado.PENDIENTE) {
            return;
        }
        reservaRepositorio.cambiarEstado(reserva.id(), Reserva.Estado.PENDIENTE,
                Reserva.Estado.CONFIRMADA, MOTIVO_CONFIRMACION);
        avisos.avisar(reserva.idUsuario(), "Reserva confirmada",
                "Recepcion confirmo tu reserva #" + reserva.id() + " al recibir el pago.");
    }

    private Reserva reservaAtendible(Long idUsuario, Long idReserva) {
        if (idReserva == null) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "La reserva es obligatoria.");
        }
        if (!recepcion.puedeOperarReserva(idUsuario, idReserva)) {
            throw new DomainException(ErrorCodes.SIN_PERMISO, "No operas la sede de esa reserva.");
        }
        return reservaRepositorio.buscar(idReserva)
                .orElseThrow(() -> new DomainException(ErrorCodes.NO_ENCONTRADO, "La reserva no existe."));
    }

    private void exigirAcceso(Long idUsuario, Long idEstablecimiento) {
        if (idEstablecimiento != null && !recepcion.puedeOperarEstablecimiento(idUsuario, idEstablecimiento)) {
            throw new DomainException(ErrorCodes.SIN_PERMISO, "No tienes acceso a esa sede.");
        }
    }

    private Instant inicioDia(LocalDate fecha) {
        LocalDate dia = fecha == null ? LocalDate.now(zona) : fecha;
        return dia.atStartOfDay(zona).toInstant();
    }

    private Instant inicioDe(LocalDate fecha, LocalTime hora) {
        if (fecha == null || hora == null) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "La fecha y la hora son obligatorias.");
        }
        return fecha.atTime(hora).atZone(zona).toInstant();
    }

    private static String normalizarMedio(String medioPago) {
        String medio = medioPago == null ? "" : medioPago.trim().toUpperCase(Locale.ROOT);
        return MEDIOS.contains(medio) ? medio : "EFECTIVO";
    }

    private static String codigo(String codigoOperacion) {
        if (codigoOperacion == null || codigoOperacion.isBlank()) {
            return null;
        }
        String limpio = codigoOperacion.trim();
        return limpio.length() > 80 ? limpio.substring(0, 80) : limpio;
    }

    /** Total congelado de la reserva: cancha con descuento + cochera opcional. */
    private static BigDecimal montoTotal(Reserva reserva) {
        BigDecimal total = reserva.montoCancha().subtract(reserva.montoDescuento());
        Reserva.CocheraReservada cochera = reserva.cochera();
        return cochera == null ? total : total.add(cochera.monto());
    }

    private static boolean esEstadoValido(String estado) {
        for (Reserva.Estado candidato : Reserva.Estado.values()) {
            if (candidato.name().equals(estado)) {
                return true;
            }
        }
        return false;
    }

    private static int acotar(int limite) {
        if (limite <= 0) {
            return 50;
        }
        return Math.min(limite, 200);
    }
}
