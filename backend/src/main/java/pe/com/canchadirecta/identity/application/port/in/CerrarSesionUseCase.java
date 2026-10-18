package pe.com.canchadirecta.identity.application.port.in;

/** Caso de uso: cerrar la sesion revocando el token de refresco. */
public interface CerrarSesionUseCase {

    void cerrar(String refreshToken);
}
