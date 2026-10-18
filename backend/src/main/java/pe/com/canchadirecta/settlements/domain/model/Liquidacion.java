package pe.com.canchadirecta.settlements.domain.model;

import pe.com.canchadirecta.shared.domain.DomainException;
import pe.com.canchadirecta.shared.domain.ErrorCodes;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Liquidacion de ingresos de un propietario en un periodo (CU04.8 / CU05.4).
 *
 * <p>Agrupa las reservas del propietario que ya fueron pagadas por el titular (un pago
 * `APROBADO`) y cuyo servicio cae dentro de `[periodoInicio, periodoFin]`. Sobre el total
 * bruto se descuenta la comision de plataforma y el neto es lo que le corresponde al
 * propietario. Cada reserva aparece como mucho en una liquidacion (RN-07).</p>
 */
public record Liquidacion(
        Long id,
        Long idPropietario,
        LocalDate periodoInicio,
        LocalDate periodoFin,
        BigDecimal montoBruto,
        BigDecimal montoComision,
        BigDecimal montoNeto,
        Estado estado
) {

    public enum Estado {
        PENDIENTE,
        PROCESADA,
        PAGADA
    }

    public Liquidacion {
        if (idPropietario == null) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "El propietario es obligatorio.");
        }
        if (periodoInicio == null || periodoFin == null) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "El periodo es obligatorio.");
        }
        if (periodoInicio.isAfter(periodoFin)) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS,
                    "El inicio del periodo no puede ser posterior a su fin.");
        }
        exigirMontoNoNegativo(montoBruto, "bruto");
        exigirMontoNoNegativo(montoComision, "de comision");
        exigirMontoNoNegativo(montoNeto, "neto");
        if (estado == null) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "El estado de la liquidacion es obligatorio.");
        }
    }

    private static void exigirMontoNoNegativo(BigDecimal monto, String nombre) {
        if (monto == null || monto.signum() < 0) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS,
                    "El monto " + nombre + " no puede ser negativo.");
        }
    }
}
