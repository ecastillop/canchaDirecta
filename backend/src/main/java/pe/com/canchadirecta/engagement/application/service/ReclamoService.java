package pe.com.canchadirecta.engagement.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.com.canchadirecta.bookings.application.port.out.ReservaRepositorio;
import pe.com.canchadirecta.bookings.domain.model.Reserva;
import pe.com.canchadirecta.engagement.application.port.in.ReclamosUseCase;
import pe.com.canchadirecta.engagement.application.port.out.ReclamoRepositorio;
import pe.com.canchadirecta.engagement.domain.model.Reclamo;
import pe.com.canchadirecta.engagement.domain.model.ReclamoSeguimiento;
import pe.com.canchadirecta.shared.application.port.out.Reloj;
import pe.com.canchadirecta.shared.domain.DomainException;
import pe.com.canchadirecta.shared.domain.ErrorCodes;

import java.util.List;

/**
 * Soporte del usuario (CU06.6): reclamos propios y su conversacion.
 *
 * <p>El usuario sale siempre del `sub` del JWT. Un reclamo puede asociarse a una reserva,
 * pero solo a una del propio usuario; el estado inicial es `ABIERTO` y solo la
 * administracion lo mueve (slice posterior). Un reclamo `CERRADO` ya no admite comentarios.</p>
 */
@ApplicationScoped
public class ReclamoService implements ReclamosUseCase {

    private static final int LIMITE_DEFECTO = 20;

    private final ReclamoRepositorio reclamos;
    private final ReservaRepositorio reservas;
    private final Reloj reloj;

    @Inject
    public ReclamoService(ReclamoRepositorio reclamos, ReservaRepositorio reservas, Reloj reloj) {
        this.reclamos = reclamos;
        this.reservas = reservas;
        this.reloj = reloj;
    }

    @Override
    @Transactional
    public Reclamo crear(Long idUsuario, Long idReserva, String asunto, String descripcion) {
        if (idReserva != null) {
            Reserva reserva = reservas.buscar(idReserva)
                    .orElseThrow(() -> new DomainException(ErrorCodes.NO_ENCONTRADO, "La reserva no existe."));
            if (!reserva.idUsuario().equals(idUsuario)) {
                throw new DomainException(ErrorCodes.SIN_PERMISO,
                        "Solo el titular puede reclamar sobre su reserva.");
            }
        }
        return reclamos.registrar(new Reclamo(null, idUsuario, idReserva, asunto, descripcion,
                Reclamo.Estado.ABIERTO, reloj.ahora()));
    }

    @Override
    public List<Reclamo> mios(Long idUsuario, int limite) {
        return reclamos.listarPorUsuario(idUsuario, limite > 0 ? limite : LIMITE_DEFECTO);
    }

    @Override
    public Detalle detalle(Long idReclamo, Long idUsuario) {
        Reclamo reclamo = propio(idReclamo, idUsuario);
        return new Detalle(reclamo, reclamos.seguimientos(reclamo.id()));
    }

    @Override
    @Transactional
    public ReclamoSeguimiento comentar(Long idReclamo, Long idUsuario, String comentario) {
        Reclamo reclamo = propio(idReclamo, idUsuario);
        if (reclamo.estado() == Reclamo.Estado.CERRADO) {
            throw new DomainException(ErrorCodes.CONFLICTO, "El reclamo esta cerrado.");
        }
        return reclamos.agregarSeguimiento(new ReclamoSeguimiento(null, reclamo.id(), idUsuario,
                comentario, reloj.ahora()));
    }

    private Reclamo propio(Long idReclamo, Long idUsuario) {
        if (idReclamo == null) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "El reclamo es obligatorio.");
        }
        Reclamo reclamo = reclamos.buscar(idReclamo)
                .orElseThrow(() -> new DomainException(ErrorCodes.NO_ENCONTRADO, "El reclamo no existe."));
        if (!reclamo.idUsuario().equals(idUsuario)) {
            throw new DomainException(ErrorCodes.SIN_PERMISO, "El reclamo pertenece a otro usuario.");
        }
        return reclamo;
    }
}
