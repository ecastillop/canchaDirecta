package pe.com.canchadirecta.identity.application.port.in;

/** Caso de uso: confirmar la recuperacion de contrasena con el token recibido. */
public interface ConfirmarRecuperacionUseCase {

    void confirmar(String tokenEnClaro, String nuevaPassword, String confirmacionPassword);
}
