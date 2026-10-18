package pe.com.canchadirecta.identity.application.port.out;

import java.time.Instant;

/**
 * Puerto de salida: notificacion al usuario.
 * La entrega real (SMTP, proveedor transaccional) queda fuera del alcance del
 * curso: el adaptador actual registra el envio de forma simulada.
 */
public interface NotificadorCuenta {

    void enviarRecuperacionPassword(String correoDestino, String tokenEnClaro, Instant expiraEn);

    /** Envia el enlace de verificacion de correo (CU01.2). */
    void enviarVerificacionCorreo(String correoDestino, String tokenEnClaro, Instant expiraEn);
}
