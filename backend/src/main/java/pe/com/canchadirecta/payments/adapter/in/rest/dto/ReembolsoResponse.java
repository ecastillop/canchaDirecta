package pe.com.canchadirecta.payments.adapter.in.rest.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** Representacion publica de un reembolso. */
public record ReembolsoResponse(
        Long id,
        BigDecimal monto,
        String motivo,
        String estado,
        Instant fechaSolicitud,
        Instant fechaResolucion
) {
}
