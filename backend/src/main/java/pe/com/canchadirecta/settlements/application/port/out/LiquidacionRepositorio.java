package pe.com.canchadirecta.settlements.application.port.out;

import pe.com.canchadirecta.settlements.application.dto.ReservaLiquidable;
import pe.com.canchadirecta.settlements.domain.model.DetalleLiquidacion;
import pe.com.canchadirecta.settlements.domain.model.Liquidacion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Puerto de salida de liquidaciones (SQL nativo, ADR-0012).
 *
 * <p><b>RN-07.</b> Una reserva aparece como mucho en una liquidacion. La garantia de fondo
 * es el `UNIQUE(id_reserva)` de `detalle_liquidacion` (V1); el servicio evita el doble
 * conteo filtrando las reservas ya liquidadas dentro de la transaccion que bloquea al
 * propietario (`bloquearPropietario`).</p>
 */
public interface LiquidacionRepositorio {

    /** Id de `propietario` del usuario, si tiene perfil de propietario. */
    Optional<Long> propietarioDe(Long idUsuario);

    /**
     * Igual que {@link #propietarioDe} pero toma el bloqueo exclusivo de la fila del
     * propietario (`SELECT ... FOR UPDATE`) para serializar la generacion de liquidaciones
     * de un mismo propietario y no contar dos veces la misma reserva (RN-07).
     */
    Optional<Long> bloquearPropietario(Long idUsuario);

    /**
     * Reservas liquidables del propietario en `[inicio, fin]`: pagadas (`pago` `APROBADO`),
     * con servicio confirmado, cuya fecha de inicio cae en el periodo y que aun no estan en
     * ninguna liquidacion.
     */
    List<ReservaLiquidable> reservasLiquidables(Long idPropietario, LocalDate inicio, LocalDate fin);

    /** Inserta la cabecera de la liquidacion y devuelve su id. */
    Long registrarLiquidacion(Long idPropietario, LocalDate inicio, LocalDate fin,
                              BigDecimal montoBruto, BigDecimal montoComision, BigDecimal montoNeto);

    /** Inserta una linea de detalle. */
    void registrarDetalle(Long idLiquidacion, DetalleLiquidacion detalle);

    /** Liquidaciones del propietario, mas recientes primero. */
    List<Liquidacion> listar(Long idPropietario);

    /** Cabecera de la liquidacion por id, si existe. */
    Optional<Liquidacion> buscar(Long idLiquidacion);

    /** ?La liquidacion pertenece a un propietario cuyo usuario es `idUsuario`? */
    boolean esPropietarioDeLiquidacion(Long idLiquidacion, Long idUsuario);

    /** Lineas de la liquidacion, ordenadas por reserva. */
    List<DetalleLiquidacion> detallesDe(Long idLiquidacion);
}
