package pe.com.canchadirecta.engagement.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import pe.com.canchadirecta.bookings.application.port.out.ReservaRepositorio;
import pe.com.canchadirecta.engagement.application.dto.ResumenCalificaciones;
import pe.com.canchadirecta.engagement.application.port.in.ConsultarCalificacionesUseCase;
import pe.com.canchadirecta.engagement.application.port.out.CalificacionRepositorio;
import pe.com.canchadirecta.shared.domain.DomainException;
import pe.com.canchadirecta.shared.domain.ErrorCodes;

/**
 * Consulta publica de calificaciones por cancha (CU02.5). Si la cancha no existe, `404`.
 */
@ApplicationScoped
public class ConsultarCalificacionesService implements ConsultarCalificacionesUseCase {

    private static final int LIMITE_DEFECTO = 20;

    private final ReservaRepositorio reservas;
    private final CalificacionRepositorio calificaciones;

    @Inject
    public ConsultarCalificacionesService(ReservaRepositorio reservas,
                                          CalificacionRepositorio calificaciones) {
        this.reservas = reservas;
        this.calificaciones = calificaciones;
    }

    @Override
    public ResumenCalificaciones deCancha(Long idCancha, int limite) {
        reservas.buscarCancha(idCancha)
                .orElseThrow(() -> new DomainException(ErrorCodes.NO_ENCONTRADO, "La cancha no existe."));
        return calificaciones.resumenDeCancha(idCancha, limite > 0 ? limite : LIMITE_DEFECTO);
    }
}
