package pe.com.canchadirecta.engagement.adapter.in.rest.dto;

import pe.com.canchadirecta.engagement.application.dto.BandejaNotificaciones;
import pe.com.canchadirecta.engagement.domain.model.Notificacion;

import java.time.Instant;
import java.util.List;

/**
 * Bandeja de avisos del usuario (mas recientes primero).
 */
public record NotificacionesResponse(
        long noLeidas,
        long total,
        List<NotificacionItem> notificaciones
) {

    public static NotificacionesResponse de(BandejaNotificaciones bandeja) {
        return new NotificacionesResponse(bandeja.noLeidas(), bandeja.total(),
                bandeja.notificaciones().stream().map(NotificacionItem::de).toList());
    }

    /** Un aviso concreto. */
    public record NotificacionItem(
            Long id,
            String titulo,
            String mensaje,
            boolean leida,
            Instant fechaEnvio
    ) {

        static NotificacionItem de(Notificacion notificacion) {
            return new NotificacionItem(notificacion.id(), notificacion.titulo(),
                    notificacion.mensaje(), notificacion.leida(), notificacion.fechaEnvio());
        }
    }
}
