package pe.com.canchadirecta.settlements.application.dto;

import pe.com.canchadirecta.settlements.domain.model.DetalleLiquidacion;
import pe.com.canchadirecta.settlements.domain.model.Liquidacion;

import java.util.List;

/** Cabecera de una liquidacion con sus lineas de reserva. */
public record LiquidacionConDetalle(Liquidacion liquidacion, List<DetalleLiquidacion> detalles) {
}
