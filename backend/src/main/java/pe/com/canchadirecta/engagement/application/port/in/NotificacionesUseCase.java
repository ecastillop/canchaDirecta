package pe.com.canchadirecta.engagement.application.port.in;

import pe.com.canchadirecta.engagement.application.dto.BandejaNotificaciones;

/**
 * Casos de uso de la bandeja de avisos del usuario.
 */
public interface NotificacionesUseCase {

    /** Notificaciones del usuario con el total y las no leidas. */
    BandejaNotificaciones bandeja(Long idUsuario, int limite);

    /** Marca una notificacion propia como leida; `404` si no existe o es ajena. */
    void marcarLeida(Long idNotificacion, Long idUsuario);
}
