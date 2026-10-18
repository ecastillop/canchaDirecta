package pe.com.canchadirecta.bookings.adapter.in.rest.dto;

import pe.com.canchadirecta.bookings.application.dto.ReservaRecibida;
import pe.com.canchadirecta.bookings.domain.model.Reserva;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Reserva tal como la ve el propietario en su bandeja (HU-008-2): los mismos campos que
 * {@link ReservaResponse} mas el titular y el estado de su ultimo pago, que son los que
 * necesita para aceptar o rechazar la solicitud (ADR-0044).
 *
 * <p>Se mantiene separado de `ReservaResponse` —que sirve al propio cliente— para que los
 * datos del titular no puedan viajar nunca por los endpoints del titular de la reserva.</p>
 */
public record ReservaRecibidaResponse(
        Long id,
        Long canchaId,
        Instant inicio,
        Instant fin,
        BigDecimal montoCancha,
        BigDecimal montoDescuento,
        String estado,
        Instant fechaCreacion,
        Long cocheraId,
        BigDecimal montoCochera,
        String cliente,
        String telefono,
        String pagoEstado
) {

    public static ReservaRecibidaResponse de(ReservaRecibida recibida) {
        Reserva reserva = recibida.reserva();
        Reserva.CocheraReservada cochera = reserva.cochera();
        return new ReservaRecibidaResponse(
                reserva.id(),
                reserva.idCancha(),
                reserva.franja().inicio(),
                reserva.franja().fin(),
                reserva.montoCancha(),
                reserva.montoDescuento(),
                reserva.estado().name(),
                reserva.fechaCreacion(),
                cochera == null ? null : cochera.idCochera(),
                cochera == null ? null : cochera.monto(),
                recibida.cliente(),
                recibida.telefono(),
                recibida.pagoEstado());
    }
}
