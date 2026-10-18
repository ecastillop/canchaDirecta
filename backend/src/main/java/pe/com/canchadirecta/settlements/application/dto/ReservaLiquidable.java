package pe.com.canchadirecta.settlements.application.dto;

import java.math.BigDecimal;

/**
 * Proyeccion de lectura de una reserva liquidable: su id y lo cobrado al titular
 * (`pago.monto`, que ya incluye la cochera opcional). Alimenta el calculo de la liquidacion.
 */
public record ReservaLiquidable(Long idReserva, BigDecimal monto) {
}
