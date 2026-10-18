package pe.com.canchadirecta.engagement.application.port.out;

import pe.com.canchadirecta.engagement.domain.model.Notificacion;

import java.time.Instant;
import java.util.List;

/**
 * Puerto de salida de notificaciones in-app (SQL nativo, ADR-0012).
 */
public interface NotificacionRepositorio {

    /** Inserta una notificacion sin leer. */
    void registrar(Long idUsuario, String titulo, String mensaje, Instant fechaEnvio);

    /** Notificaciones del usuario, las mas recientes primero. */
    List<Notificacion> listarPorUsuario(Long idUsuario, int limite);

    /** Numero de notificaciones sin leer del usuario. */
    long contarNoLeidas(Long idUsuario);

    /** Marca la notificacion como leida si pertenece al usuario; `false` si no existe o es ajena. */
    boolean marcarLeida(Long idNotificacion, Long idUsuario);
}
