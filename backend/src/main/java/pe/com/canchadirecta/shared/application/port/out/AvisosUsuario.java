package pe.com.canchadirecta.shared.application.port.out;

/**
 * Aviso in-app al usuario (tabla `notificacion`). Puerto compartido para que los modulos
 * que cambian el estado de una reserva o de un pago dejen constancia sin depender del
 * modulo `engagement` (evita una dependencia circular).
 */
public interface AvisosUsuario {

    /** Registra una notificacion para el usuario indicado. */
    void avisar(Long idUsuario, String titulo, String mensaje);
}
