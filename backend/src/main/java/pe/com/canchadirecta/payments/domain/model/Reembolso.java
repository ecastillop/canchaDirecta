package pe.com.canchadirecta.payments.domain.model;

import pe.com.canchadirecta.shared.domain.DomainException;
import pe.com.canchadirecta.shared.domain.ErrorCodes;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Reembolso asociado a un pago (`reembolso`). Cuando la politica economica se aplica de
 * forma automatica (cancelacion dentro de la ventana gratuita o rechazo del propietario),
 * el reembolso queda directamente en estado `PROCESADO`.
 */
public record Reembolso(
        Long id,
        Long idPago,
        BigDecimal monto,
        String motivo,
        Estado estado,
        Instant fechaSolicitud,
        Instant fechaResolucion
) {

    public enum Estado {
        SOLICITADO,
        APROBADO,
        RECHAZADO,
        PROCESADO
    }

    /** Longitud de `reembolso.motivo` (VARCHAR(250)). */
    public static final int MOTIVO_MAXIMO = 250;

    public Reembolso {
        if (idPago == null) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "El pago del reembolso es obligatorio.");
        }
        if (monto == null || monto.signum() <= 0) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "El monto del reembolso debe ser mayor que cero.");
        }
        if (motivo == null || motivo.isBlank()) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "El motivo del reembolso es obligatorio.");
        }
        if (motivo.length() > MOTIVO_MAXIMO) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS,
                    "El motivo del reembolso no puede superar los " + MOTIVO_MAXIMO + " caracteres.");
        }
        if (estado == null) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "El estado del reembolso es obligatorio.");
        }
    }
}
