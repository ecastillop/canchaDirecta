package pe.com.canchadirecta.reception.application.dto;

/** KPIs del turno actual de recepcion. */
public record ResumenTurno(
        long reservasDelDia,
        long checkIns,
        long cobrosPendientes,
        long canchasDisponibles) {
}
