package pe.com.canchadirecta.identity;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sustituye al notificador real durante los tests para poder capturar el token que
 * se habria enviado por correo (el valor en claro solo existe en el momento del envio).
 */
@Alternative
@jakarta.annotation.Priority(1)
@ApplicationScoped
public class NotificadorCuentaFalso implements pe.com.canchadirecta.identity.application.port.out.NotificadorCuenta {

    private final Map<String, String> tokensPorCorreo = new ConcurrentHashMap<>();
    private final Map<String, String> verificacionesPorCorreo = new ConcurrentHashMap<>();

    @Override
    public void enviarRecuperacionPassword(String correoDestino, String tokenEnClaro, Instant expiraEn) {
        tokensPorCorreo.put(correoDestino, tokenEnClaro);
    }

    @Override
    public void enviarVerificacionCorreo(String correoDestino, String tokenEnClaro, Instant expiraEn) {
        verificacionesPorCorreo.put(correoDestino, tokenEnClaro);
    }

    public String tokenDe(String correo) {
        return tokensPorCorreo.get(correo);
    }

    public String tokenVerificacionDe(String correo) {
        return verificacionesPorCorreo.get(correo);
    }

    public void limpiar() {
        tokensPorCorreo.clear();
        verificacionesPorCorreo.clear();
    }
}
