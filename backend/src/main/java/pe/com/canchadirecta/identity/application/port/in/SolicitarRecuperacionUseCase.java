package pe.com.canchadirecta.identity.application.port.in;

/** Caso de uso: solicitar el enlace de recuperacion de contrasena. */
public interface SolicitarRecuperacionUseCase {

    /** No revela si el correo existe: el resultado es siempre el mismo. */
    void solicitar(String correo);
}
