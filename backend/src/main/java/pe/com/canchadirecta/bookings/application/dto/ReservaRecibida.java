package pe.com.canchadirecta.bookings.application.dto;

import pe.com.canchadirecta.bookings.domain.model.Reserva;

/**
 * Reserva vista desde la bandeja del propietario (HU-008-2): la reserva mas los datos que
 * necesita para decidir si la atiende —quien la solicito y si ya pago— sin exponerlos en
 * la vista del propio cliente (ADR-0044).
 *
 * <p>{@code pagoEstado} es el estado del ultimo pago registrado ({@code APROBADO},
 * {@code RECHAZADO} o {@code SOLICITADO}) y es nulo cuando la reserva aun no tiene pagos.</p>
 */
public record ReservaRecibida(
        Reserva reserva,
        String cliente,
        String telefono,
        String pagoEstado
) {
}
