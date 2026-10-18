package pe.com.canchadirecta.shared.application.port.out;

import java.time.Instant;

/**
 * Puerto de salida que aplica la politica economica cuando una reserva deja de ser
 * vigente (cancelacion por el titular o rechazo por el propietario). Vive en `shared`
 * para que `bookings` pueda invocarlo sin depender del modulo `payments`, que es quien
 * lo implementa.
 *
 * <p>Se invoca dentro de la misma transaccion que cambia el estado de la reserva, de modo
 * que el cambio de estado y el reembolso (si procede) son atomicos.</p>
 */
public interface PoliticaReembolso {

    /**
     * Aplica la politica de reembolso a una reserva que acaba de liberar su franja.
     *
     * @param idReserva     reserva liberada
     * @param inicioReserva inicio de la franja reservada (para medir la ventana de cancelacion)
     * @param momento       instante en que se libera
     * @param porPropietario {@code true} si la libera el propietario (rechazo): reembolso
     *                       total; {@code false} si la cancela el titular: reembolso solo si
     *                       se cancela dentro de la ventana gratuita
     */
    void alLiberarReserva(Long idReserva, Instant inicioReserva, Instant momento, boolean porPropietario);
}
