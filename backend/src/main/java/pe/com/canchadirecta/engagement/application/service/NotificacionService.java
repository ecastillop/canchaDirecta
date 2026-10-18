package pe.com.canchadirecta.engagement.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.com.canchadirecta.engagement.application.dto.BandejaNotificaciones;
import pe.com.canchadirecta.engagement.application.port.in.NotificacionesUseCase;
import pe.com.canchadirecta.engagement.application.port.out.NotificacionRepositorio;
import pe.com.canchadirecta.engagement.domain.model.Notificacion;
import pe.com.canchadirecta.shared.domain.DomainException;
import pe.com.canchadirecta.shared.domain.ErrorCodes;

import java.util.List;

/**
 * Bandeja de avisos in-app del usuario: listar y marcar como leida.
 */
@ApplicationScoped
public class NotificacionService implements NotificacionesUseCase {

    private static final int LIMITE_DEFECTO = 20;

    private final NotificacionRepositorio notificaciones;

    @Inject
    public NotificacionService(NotificacionRepositorio notificaciones) {
        this.notificaciones = notificaciones;
    }

    @Override
    public BandejaNotificaciones bandeja(Long idUsuario, int limite) {
        List<Notificacion> lista = notificaciones.listarPorUsuario(idUsuario,
                limite > 0 ? limite : LIMITE_DEFECTO);
        return new BandejaNotificaciones(notificaciones.contarNoLeidas(idUsuario), lista.size(), lista);
    }

    @Override
    @Transactional
    public void marcarLeida(Long idNotificacion, Long idUsuario) {
        if (idNotificacion == null) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "La notificacion es obligatoria.");
        }
        if (!notificaciones.marcarLeida(idNotificacion, idUsuario)) {
            throw new DomainException(ErrorCodes.NO_ENCONTRADO, "La notificacion no existe.");
        }
    }
}
