package pe.com.canchadirecta.settlements.adapter.in.rest.dto;

import java.math.BigDecimal;
import java.util.List;

/** Representacion publica de una liquidacion con sus lineas de reserva. */
public record LiquidacionResponse(
        Long id,
        String periodoInicio,
        String periodoFin,
        BigDecimal montoBruto,
        BigDecimal montoComision,
        BigDecimal montoNeto,
        String estado,
        List<Detalle> detalles
) {

    /** Linea de la liquidacion: reserva liquidada y su desglose de comision. */
    public record Detalle(
            Long reservaId,
            BigDecimal montoReserva,
            BigDecimal porcentajeComision,
            BigDecimal montoComision,
            BigDecimal montoNeto
    ) {
    }
}
