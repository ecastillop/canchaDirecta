package pe.com.canchadirecta.settlements.application.port.in;

import pe.com.canchadirecta.settlements.application.dto.LiquidacionConDetalle;
import pe.com.canchadirecta.settlements.domain.model.Liquidacion;

import java.time.LocalDate;
import java.util.List;

/**
 * Casos de uso de liquidaciones del propietario (CU04.8 / CU05.2 / CU05.4).
 */
public interface GestionarLiquidacionesUseCase {

    /**
     * Genera la liquidacion de las reservas pagadas y confirmadas del propietario cuyo
     * servicio cae en el periodo. `409` si el usuario no es propietario o si no tiene
     * reservas liquidables en el periodo.
     */
    LiquidacionConDetalle generar(Long idUsuario, LocalDate periodoInicio, LocalDate periodoFin);

    /** Liquidaciones del propietario autenticado (sin lineas de detalle). */
    List<Liquidacion> listar(Long idUsuario);

    /** Liquidacion del propietario autenticado con sus lineas; `403` si es de otro. */
    LiquidacionConDetalle consultar(Long idUsuario, Long idLiquidacion);
}
