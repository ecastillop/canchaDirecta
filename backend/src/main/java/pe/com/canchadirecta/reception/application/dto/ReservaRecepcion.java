package pe.com.canchadirecta.reception.application.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** Reserva vista desde recepcion, con sede, cliente y estado del pago. */
public record ReservaRecepcion(
        Long id,
        Long idEstablecimiento,
        String establecimiento,
        Long idCancha,
        String cancha,
        Instant inicio,
        Instant fin,
        String estado,
        String pagoEstado,
        BigDecimal total,
        Long idCliente,
        String cliente,
        String telefono) {
}
