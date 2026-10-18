package pe.com.canchadirecta.identity.adapter.out.notification;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import pe.com.canchadirecta.identity.application.port.out.NotificadorCuenta;

import java.time.Instant;

/**
 * Notificador simulado: en lugar de enviar un correo real, registra el envio en el
 * log. Suficiente para el alcance del proyecto (no hay proveedor SMTP configurado) y
 * permite completar el flujo manualmente leyendo el token del log.
 *
 * <p>El registro del token en claro se puede desactivar con
 * `cd.notificaciones.registrar-token-en-log=false`; en un despliegue real se sustituye
 * este adaptador por uno que use un proveedor de correo.
 */
@ApplicationScoped
public class LogNotificadorCuenta implements NotificadorCuenta {

    private static final Logger LOG = Logger.getLogger(LogNotificadorCuenta.class);

    @ConfigProperty(name = "cd.notificaciones.registrar-token-en-log", defaultValue = "true")
    boolean registrarTokenEnLog;

    @Override
    public void enviarRecuperacionPassword(String correoDestino, String tokenEnClaro, Instant expiraEn) {
        if (registrarTokenEnLog) {
            LOG.infof("[SIMULADO] Recuperacion de contrasena para %s (expira %s). Token: %s",
                    correoDestino, expiraEn, tokenEnClaro);
        } else {
            LOG.infof("[SIMULADO] Recuperacion de contrasena enviada a %s (expira %s)",
                    correoDestino, expiraEn);
        }
    }

    @Override
    public void enviarVerificacionCorreo(String correoDestino, String tokenEnClaro, Instant expiraEn) {
        if (registrarTokenEnLog) {
            LOG.infof("[SIMULADO] Verificacion de correo para %s (expira %s). Token: %s",
                    correoDestino, expiraEn, tokenEnClaro);
        } else {
            LOG.infof("[SIMULADO] Verificacion de correo enviada a %s (expira %s)",
                    correoDestino, expiraEn);
        }
    }
}
