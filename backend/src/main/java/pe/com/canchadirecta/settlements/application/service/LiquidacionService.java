package pe.com.canchadirecta.settlements.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import pe.com.canchadirecta.settlements.application.dto.LiquidacionConDetalle;
import pe.com.canchadirecta.settlements.application.dto.ReservaLiquidable;
import pe.com.canchadirecta.settlements.application.port.in.GestionarLiquidacionesUseCase;
import pe.com.canchadirecta.settlements.application.port.out.LiquidacionRepositorio;
import pe.com.canchadirecta.settlements.domain.model.DetalleLiquidacion;
import pe.com.canchadirecta.settlements.domain.model.Liquidacion;
import pe.com.canchadirecta.shared.domain.DomainException;
import pe.com.canchadirecta.shared.domain.ErrorCodes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Liquidacion de ingresos del propietario (CU04.8 / CU05.2 / CU05.4).
 *
 * <p>Toma las reservas pagadas y confirmadas del propietario cuyo inicio cae en el periodo,
 * aplica la comision de plataforma ({@code cd.liquidaciones.porcentaje-comision}) y guarda
 * la cabecera con una linea por reserva. El neto del propietario es el bruto menos la
 * comision.</p>
 *
 * <p><b>RN-07.</b> Una reserva no puede liquidarse dos veces. La transaccion bloquea la fila
 * del propietario antes de leer las reservas liquidables, de modo que dos generaciones
 * simultaneas del mismo propietario se serializan; el `UNIQUE(id_reserva)` de
 * `detalle_liquidacion` es la garantia final.</p>
 */
@ApplicationScoped
public class LiquidacionService implements GestionarLiquidacionesUseCase {

    private final LiquidacionRepositorio liquidaciones;
    private final BigDecimal porcentajeComision;

    @Inject
    public LiquidacionService(
            LiquidacionRepositorio liquidaciones,
            @ConfigProperty(name = "cd.liquidaciones.porcentaje-comision", defaultValue = "10")
            BigDecimal porcentajeComision) {
        if (porcentajeComision.signum() < 0 || porcentajeComision.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalStateException("cd.liquidaciones.porcentaje-comision debe estar entre 0 y 100.");
        }
        this.liquidaciones = liquidaciones;
        this.porcentajeComision = porcentajeComision.setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    @Transactional
    public LiquidacionConDetalle generar(Long idUsuario, LocalDate periodoInicio, LocalDate periodoFin) {
        if (periodoInicio == null || periodoFin == null) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "El periodo es obligatorio.");
        }
        if (periodoInicio.isAfter(periodoFin)) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS,
                    "El inicio del periodo no puede ser posterior a su fin.");
        }

        Long idPropietario = liquidaciones.bloquearPropietario(idUsuario)
                .orElseThrow(() -> new DomainException(ErrorCodes.CONFLICTO,
                        "El usuario no tiene perfil de propietario con establecimientos."));

        List<ReservaLiquidable> reservas = liquidaciones.reservasLiquidables(idPropietario, periodoInicio, periodoFin);
        if (reservas.isEmpty()) {
            throw new DomainException(ErrorCodes.CONFLICTO,
                    "No hay reservas pagadas y confirmadas sin liquidar en el periodo.");
        }

        List<DetalleLiquidacion> detalles = new ArrayList<>(reservas.size());
        BigDecimal bruto = BigDecimal.ZERO;
        BigDecimal comision = BigDecimal.ZERO;
        BigDecimal neto = BigDecimal.ZERO;
        for (ReservaLiquidable reserva : reservas) {
            DetalleLiquidacion detalle = calcular(reserva);
            detalles.add(detalle);
            bruto = bruto.add(detalle.montoReserva());
            comision = comision.add(detalle.montoComision());
            neto = neto.add(detalle.montoNeto());
        }

        Long idLiquidacion = liquidaciones.registrarLiquidacion(idPropietario, periodoInicio, periodoFin,
                bruto, comision, neto);
        for (DetalleLiquidacion detalle : detalles) {
            liquidaciones.registrarDetalle(idLiquidacion, detalle);
        }

        Liquidacion liquidacion = new Liquidacion(idLiquidacion, idPropietario, periodoInicio, periodoFin,
                bruto, comision, neto, Liquidacion.Estado.PENDIENTE);
        return new LiquidacionConDetalle(liquidacion, detalles);
    }

    @Override
    public List<Liquidacion> listar(Long idUsuario) {
        return liquidaciones.propietarioDe(idUsuario)
                .map(liquidaciones::listar)
                .orElseGet(List::of);
    }

    @Override
    public LiquidacionConDetalle consultar(Long idUsuario, Long idLiquidacion) {
        Liquidacion liquidacion = liquidaciones.buscar(idLiquidacion)
                .orElseThrow(() -> new DomainException(ErrorCodes.NO_ENCONTRADO, "La liquidacion no existe."));
        if (!liquidaciones.esPropietarioDeLiquidacion(idLiquidacion, idUsuario)) {
            throw new DomainException(ErrorCodes.SIN_PERMISO, "Solo el propietario puede ver su liquidacion.");
        }
        return new LiquidacionConDetalle(liquidacion, liquidaciones.detallesDe(idLiquidacion));
    }

    /** Comision por linea redondeada a 2 decimales; el neto es el bruto menos la comision. */
    private DetalleLiquidacion calcular(ReservaLiquidable reserva) {
        BigDecimal monto = reserva.monto().setScale(2, RoundingMode.HALF_UP);
        BigDecimal comision = monto.multiply(porcentajeComision)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        return new DetalleLiquidacion(reserva.idReserva(), monto, porcentajeComision, comision,
                monto.subtract(comision));
    }
}
