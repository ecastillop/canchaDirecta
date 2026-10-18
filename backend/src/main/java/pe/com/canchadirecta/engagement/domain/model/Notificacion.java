package pe.com.canchadirecta.engagement.domain.model;

import java.time.Instant;

/**
 * Notificacion in-app de un usuario (tabla `notificacion`). Se genera en los eventos que le
 * afectan (reserva confirmada/rechazada/revocada/finalizada, pago aprobado) y se consulta en
 * la bandeja de avisos.
 */
public record Notificacion(
        Long id,
        Long idUsuario,
        String titulo,
        String mensaje,
        boolean leida,
        Instant fechaEnvio
) {

    public static final int TITULO_MAX = 120;
    public static final int MENSAJE_MAX = 500;
}
