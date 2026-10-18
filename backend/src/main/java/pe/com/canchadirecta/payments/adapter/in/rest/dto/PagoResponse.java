package pe.com.canchadirecta.payments.adapter.in.rest.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Representacion publica de un pago con sus reembolsos. */
public record PagoResponse(
        Long id,
        Long reservaId,
        BigDecimal monto,
        String medioPago,
        String codigoOperacion,
        String estado,
        Instant fechaPago,
        List<ReembolsoResponse> reembolsos
) {
}
