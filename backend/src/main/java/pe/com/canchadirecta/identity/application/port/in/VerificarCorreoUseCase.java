package pe.com.canchadirecta.identity.application.port.in;

/** Verificacion del correo electronico mediante el token de un solo uso (CU01.2). */
public interface VerificarCorreoUseCase {

    void verificar(String token);
}
