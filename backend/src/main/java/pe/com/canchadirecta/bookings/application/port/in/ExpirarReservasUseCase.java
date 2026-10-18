package pe.com.canchadirecta.bookings.application.port.in;

/**
 * Expiracion de solicitudes de reserva que no completaron su pago (ADR-0022).
 *
 * <p>Una reserva `PENDIENTE` sin pago `APROBADO` que supera el hold de pago de su
 * establecimiento (`config_establecimiento.hold_pago_min`, o el valor por defecto) deja
 * de tener sentido mantenerla: retiene una franja que nadie va a usar y nadie la va a
 * atender. El caso de uso la pasa a `EXPIRADA`, lo que libera la franja (la ocupacion
 * solo cuenta `PENDIENTE` y `CONFIRMADA`) y deja la traza en `historial_reserva`.</p>
 */
public interface ExpirarReservasUseCase {

    /**
     * Expira las reservas `PENDIENTE` sin pago aprobado cuyo hold de pago ya vencio.
     *
     * @param max maximo de reservas a expirar en esta ejecucion (acota el lote)
     * @return numero de reservas efectivamente expiradas
     */
    int expirarVencidas(int max);
}
