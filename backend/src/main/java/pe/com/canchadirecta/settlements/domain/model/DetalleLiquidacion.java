package pe.com.canchadirecta.settlements.domain.model;

import pe.com.canchadirecta.shared.domain.DomainException;
import pe.com.canchadirecta.shared.domain.ErrorCodes;

import java.math.BigDecimal;

/**
 * Linea de una liquidacion: la parte de una reserva incluida en ella (RN-07).
 *
 * <p>`montoReserva` es lo efectivamente cobrado al titular (incluida la cochera, si la
 * hubiera); `porcentajeComision` es la tasa de plataforma aplicada; `montoComision` su
 * equivalente monetario y `montoNeto` lo que percibe el propietario
 * (`montoReserva - montoComision`).</p>
 */
public record DetalleLiquidacion(
        Long idReserva,
        BigDecimal montoReserva,
        BigDecimal porcentajeComision,
        BigDecimal montoComision,
        BigDecimal montoNeto
) {

    /** Limite del `CHECK` `ck_detalle_liquidacion_porcentaje` (0..100). */
    private static final BigDecimal CIEN = new BigDecimal("100");

    public DetalleLiquidacion {
        if (idReserva == null) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "La reserva del detalle es obligatoria.");
        }
        exigirNoNegativo(montoReserva, "El monto de la reserva");
        if (porcentajeComision == null || porcentajeComision.signum() < 0
                || porcentajeComision.compareTo(CIEN) > 0) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS,
                    "El porcentaje de comision debe estar entre 0 y 100.");
        }
        exigirNoNegativo(montoComision, "El monto de comision");
        exigirNoNegativo(montoNeto, "El monto neto");
    }

    private static void exigirNoNegativo(BigDecimal monto, String nombre) {
        if (monto == null || monto.signum() < 0) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, nombre + " no puede ser negativo.");
        }
    }
}
