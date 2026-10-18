package pe.com.canchadirecta.bookings.adapter.in.rest.dto;

import pe.com.canchadirecta.bookings.domain.model.Reserva;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Representacion publica de una reserva. Se separa del agregado de dominio para no
 * exponer campos internos (como el usuario titular, que se deduce del token) y para
 * mantener el mismo nombre de campo que la peticion (`canchaId`).
 */
public record ReservaResponse(
        Long id,
        Long canchaId,
        Instant inicio,
        Instant fin,
        BigDecimal montoCancha,
        BigDecimal montoDescuento,
        String estado,
        Instant fechaCreacion,
        Long cocheraId,
        BigDecimal montoCochera
) {

    public static ReservaResponse de(Reserva reserva) {
        Reserva.CocheraReservada cochera = reserva.cochera();
        return new ReservaResponse(
                reserva.id(),
                reserva.idCancha(),
                reserva.franja().inicio(),
                reserva.franja().fin(),
                reserva.montoCancha(),
                reserva.montoDescuento(),
                reserva.estado().name(),
                reserva.fechaCreacion(),
                cochera == null ? null : cochera.idCochera(),
                cochera == null ? null : cochera.monto());
    }
}
