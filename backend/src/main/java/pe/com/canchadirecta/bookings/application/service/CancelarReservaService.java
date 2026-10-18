package pe.com.canchadirecta.bookings.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import pe.com.canchadirecta.bookings.application.port.in.CancelarReservaUseCase;
import pe.com.canchadirecta.bookings.application.port.out.ReservaRepositorio;
import pe.com.canchadirecta.bookings.domain.model.Reserva;
import pe.com.canchadirecta.shared.application.port.out.PoliticaReembolso;
import pe.com.canchadirecta.shared.application.port.out.Reloj;
import pe.com.canchadirecta.shared.domain.DomainException;
import pe.com.canchadirecta.shared.domain.ErrorCodes;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Cancelacion de una reserva por su titular.
 *
 * <p>El metodo es transaccional y lee la reserva con bloqueo exclusivo de su fila
 * (`FOR UPDATE`), de modo que dos cancelaciones simultaneas de la misma reserva no
 * pueden registrarse dos veces: la segunda ve el estado ya cancelado y recibe 409.</p>
 *
 * <p>Al pasar a `CANCELADA` la franja deja de retener cupo, por lo que vuelve a
 * aparecer en la disponibilidad y puede reservarse de nuevo. Ademas, dentro de la misma
 * transaccion se aplica la politica de reembolso del titular ({@link PoliticaReembolso}):
 * si la reserva tenia un pago aprobado y se cancela dentro de la ventana gratuita, se
 * genera el reembolso (L-12).</p>
 */
@ApplicationScoped
public class CancelarReservaService implements CancelarReservaUseCase {

    private final ReservaRepositorio reservas;
    private final PoliticaReembolso reembolsos;
    private final Reloj reloj;
    private final long horasMinimas;

    @Inject
    public CancelarReservaService(
            ReservaRepositorio reservas,
            PoliticaReembolso reembolsos,
            Reloj reloj,
            @ConfigProperty(name = "cd.reservas.horas-minimas-cancelacion", defaultValue = "2") long horasMinimas) {
        this.reservas = reservas;
        this.reembolsos = reembolsos;
        this.reloj = reloj;
        this.horasMinimas = horasMinimas;
    }

    @Override
    @Transactional
    public Reserva cancelar(Long idReserva, Long idUsuario) {
        if (idReserva == null) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "La reserva es obligatoria.");
        }
        Reserva reserva = reservas.buscarParaActualizar(idReserva)
                .orElseThrow(() -> new DomainException(ErrorCodes.NO_ENCONTRADO, "La reserva no existe."));

        // El titular sale del token: nadie cancela reservas ajenas.
        if (!reserva.idUsuario().equals(idUsuario)) {
            throw new DomainException(ErrorCodes.SIN_PERMISO, "Solo el titular puede cancelar su reserva.");
        }
        if (reserva.estado() == Reserva.Estado.CANCELADA) {
            throw new DomainException(ErrorCodes.CONFLICTO, "La reserva ya estaba cancelada.");
        }
        if (reserva.estado() == Reserva.Estado.FINALIZADA) {
            throw new DomainException(ErrorCodes.CONFLICTO, "Una reserva finalizada no se puede cancelar.");
        }

        Instant ahora = reloj.ahora();
        if (!reserva.franja().inicio().isAfter(ahora)) {
            throw new DomainException(ErrorCodes.CONFLICTO, "La reserva ya comenzo y no se puede cancelar.");
        }
        if (reserva.franja().inicio().isBefore(ahora.plus(horasMinimas, ChronoUnit.HOURS))) {
            throw new DomainException(ErrorCodes.CONFLICTO,
                    "Solo se puede cancelar hasta " + horasMinimas + " horas antes del inicio.");
        }

        reservas.cambiarEstado(idReserva, reserva.estado(), Reserva.Estado.CANCELADA,
                "Cancelada por el titular");
        reembolsos.alLiberarReserva(idReserva, reserva.franja().inicio(), ahora, false);
        return new Reserva(reserva.id(), reserva.idUsuario(), reserva.idCancha(), reserva.franja(),
                reserva.montoCancha(), reserva.montoDescuento(), Reserva.Estado.CANCELADA,
                reserva.fechaCreacion(), reserva.cochera());
    }
}
