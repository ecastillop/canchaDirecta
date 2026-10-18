package pe.com.canchadirecta.payments.domain.model;

import pe.com.canchadirecta.shared.domain.DomainException;
import pe.com.canchadirecta.shared.domain.ErrorCodes;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Pago de una reserva (`pago`, RN-05). El simulador local crea como maximo un pago
 * `APROBADO` por reserva; la unicidad la garantiza la columna generada `id_reserva_aprobada`
 * + su `UNIQUE` (ADR-0006). Los intentos `RECHAZADO` no ocupan ese cupo, por lo que el
 * titular puede reintentar el pago.
 */
public record Pago(
        Long id,
        Long idReserva,
        BigDecimal monto,
        String medioPago,
        String codigoOperacion,
        Estado estado,
        Instant fechaPago
) {

    public enum Estado {
        PENDIENTE,
        APROBADO,
        RECHAZADO,
        REEMBOLSADO
    }

    /** Longitud de `pago.medio_pago` (VARCHAR(30)). */
    public static final int MEDIO_MAXIMO = 30;

    public Pago {
        if (idReserva == null) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "La reserva del pago es obligatoria.");
        }
        if (monto == null || monto.signum() <= 0) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "El monto del pago debe ser mayor que cero.");
        }
        if (medioPago == null || medioPago.isBlank()) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "El medio de pago es obligatorio.");
        }
        if (medioPago.length() > MEDIO_MAXIMO) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS,
                    "El medio de pago no puede superar los " + MEDIO_MAXIMO + " caracteres.");
        }
        if (estado == null) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "El estado del pago es obligatorio.");
        }
    }
}
