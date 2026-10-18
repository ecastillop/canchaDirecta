package pe.com.canchadirecta.identity.application.port.in;

/** Reenvio del enlace de verificacion de correo (CU01.2). */
public interface ReenviarVerificacionUseCase {

    /** Nunca revela si la cuenta existe o ya estaba verificada. */
    void reenviar(String correo);
}
