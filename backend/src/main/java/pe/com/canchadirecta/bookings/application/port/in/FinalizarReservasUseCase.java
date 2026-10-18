package pe.com.canchadirecta.bookings.application.port.in;

/**
 * Cierre automatico de reservas confirmadas cuyo bloque horario ya concluyo (L-15).
 *
 * <p>Una reserva `CONFIRMADA` cuyo `fin` quedo en el pasado no puede volver a usarse ni
 * atenderse: pasa a `FINALIZADA` sin tocar el importe (el titular dispuso de su franja; no
 * corresponde reembolso). Cierra el ciclo de vida de la reserva y habilita el cierre
 * contable del propietario: las liquidaciones ya cuentan `CONFIRMADA` y `FINALIZADA`
 * (RN-07, ADR-0021).</p>
 */
public interface FinalizarReservasUseCase {

    /**
     * Finaliza las reservas `CONFIRMADA` cuyo bloque ya concluyo.
     *
     * @param max maximo de reservas a finalizar en esta ejecucion (acota el lote)
     * @return numero de reservas efectivamente finalizadas
     */
    int finalizarConcluidas(int max);
}
