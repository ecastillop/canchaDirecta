package pe.com.canchadirecta.engagement.application.dto;

import pe.com.canchadirecta.engagement.domain.model.Notificacion;

import java.util.List;

/**
 * Bandeja de avisos: las mas recientes, el total devuelto y cuantas siguen sin leer.
 */
public record BandejaNotificaciones(
        long noLeidas,
        long total,
        List<Notificacion> notificaciones
) {
}
