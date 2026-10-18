package pe.com.canchadirecta.settlements.adapter.out.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import pe.com.canchadirecta.settlements.application.dto.ReservaLiquidable;
import pe.com.canchadirecta.settlements.application.port.out.LiquidacionRepositorio;
import pe.com.canchadirecta.settlements.domain.model.DetalleLiquidacion;
import pe.com.canchadirecta.settlements.domain.model.Liquidacion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Persistencia de liquidaciones con SQL nativo (ADR-0012).
 *
 * <p>El unico dato que cruza de modulo es una <b>proyeccion de lectura</b>: la consulta de
 * reservas liquidables une `reserva` (bookings) y `pago` (payments) para agregar ingresos
 * (patron read model). La escritura queda confinada a las tablas propias `liquidacion` y
 * `detalle_liquidacion`, de modo que `settlements` no depende de las clases de otros
 * modulos (ADR-0021).</p>
 *
 * <p>Todas las operaciones se unen a la transaccion del caso de uso (propagacion REQUIRED).</p>
 */
@ApplicationScoped
public class LiquidacionPersistenciaAdapter implements LiquidacionRepositorio {

    /** Columnas de `liquidacion` en el orden que espera {@link #aLiquidacion(Object[])}. */
    private static final String COLUMNAS_LIQUIDACION = "id_liquidacion, id_propietario, "
            + "CAST(periodo_inicio AS CHAR), CAST(periodo_fin AS CHAR), "
            + "monto_bruto, monto_comision, monto_neto, estado";

    private final EntityManager em;

    @Inject
    public LiquidacionPersistenciaAdapter(EntityManager em) {
        this.em = em;
    }

    @Override
    public Optional<Long> propietarioDe(Long idUsuario) {
        return buscarPropietario("SELECT id_propietario FROM propietario WHERE id_usuario = :id",
                idUsuario);
    }

    @Override
    @Transactional
    public Optional<Long> bloquearPropietario(Long idUsuario) {
        return buscarPropietario("SELECT id_propietario FROM propietario WHERE id_usuario = :id FOR UPDATE",
                idUsuario);
    }

    private Optional<Long> buscarPropietario(String sql, Long idUsuario) {
        List<?> filas = em.createNativeQuery(sql)
                .setParameter("id", idUsuario.intValue())
                .setMaxResults(1)
                .getResultList();
        return filas.isEmpty() ? Optional.empty() : Optional.of(((Number) filas.get(0)).longValue());
    }

    @Override
    public List<ReservaLiquidable> reservasLiquidables(Long idPropietario, LocalDate inicio, LocalDate fin) {
        // Read model: reservas del propietario pagadas (APROBADO), con servicio confirmado,
        // cuya fecha de inicio cae en el periodo y que aun no estan liquidadas (RN-07).
        List<?> filas = em.createNativeQuery(
                        "SELECT r.id_reserva, p.monto FROM reserva r "
                                + "JOIN cancha c ON c.id_cancha = r.id_cancha "
                                + "JOIN establecimiento e ON e.id_establecimiento = c.id_establecimiento "
                                + "JOIN pago p ON p.id_reserva = r.id_reserva AND p.estado = 'APROBADO' "
                                + "WHERE e.id_propietario = :idPropietario "
                                + "AND r.estado IN ('CONFIRMADA','FINALIZADA') "
                                + "AND DATE(r.inicio) BETWEEN :inicio AND :fin "
                                + "AND NOT EXISTS (SELECT 1 FROM detalle_liquidacion dl "
                                + "WHERE dl.id_reserva = r.id_reserva) "
                                + "ORDER BY r.id_reserva")
                .setParameter("idPropietario", idPropietario.intValue())
                .setParameter("inicio", inicio.toString())
                .setParameter("fin", fin.toString())
                .getResultList();

        List<ReservaLiquidable> reservas = new ArrayList<>(filas.size());
        for (Object fila : filas) {
            Object[] f = (Object[]) fila;
            reservas.add(new ReservaLiquidable(((Number) f[0]).longValue(), (BigDecimal) f[1]));
        }
        return reservas;
    }

    @Override
    @Transactional
    public Long registrarLiquidacion(Long idPropietario, LocalDate inicio, LocalDate fin,
                                     BigDecimal montoBruto, BigDecimal montoComision, BigDecimal montoNeto) {
        em.createNativeQuery("INSERT INTO liquidacion "
                        + "(id_propietario, periodo_inicio, periodo_fin, monto_bruto, monto_comision, "
                        + "monto_neto, estado) "
                        + "VALUES (:idPropietario, :inicio, :fin, :bruto, :comision, :neto, 'PENDIENTE')")
                .setParameter("idPropietario", idPropietario.intValue())
                .setParameter("inicio", inicio.toString())
                .setParameter("fin", fin.toString())
                .setParameter("bruto", montoBruto)
                .setParameter("comision", montoComision)
                .setParameter("neto", montoNeto)
                .executeUpdate();
        return ultimoIdGenerado();
    }

    @Override
    @Transactional
    public void registrarDetalle(Long idLiquidacion, DetalleLiquidacion detalle) {
        em.createNativeQuery("INSERT INTO detalle_liquidacion "
                        + "(id_liquidacion, id_reserva, monto_reserva, porcentaje_comision, "
                        + "monto_comision, monto_neto) "
                        + "VALUES (:idLiquidacion, :idReserva, :montoReserva, :porcentaje, :comision, :neto)")
                .setParameter("idLiquidacion", idLiquidacion)
                .setParameter("idReserva", detalle.idReserva())
                .setParameter("montoReserva", detalle.montoReserva())
                .setParameter("porcentaje", detalle.porcentajeComision())
                .setParameter("comision", detalle.montoComision())
                .setParameter("neto", detalle.montoNeto())
                .executeUpdate();
    }

    @Override
    public List<Liquidacion> listar(Long idPropietario) {
        List<?> filas = em.createNativeQuery("SELECT " + COLUMNAS_LIQUIDACION
                        + " FROM liquidacion WHERE id_propietario = :id ORDER BY id_liquidacion DESC")
                .setParameter("id", idPropietario.intValue())
                .getResultList();

        List<Liquidacion> liquidaciones = new ArrayList<>(filas.size());
        for (Object fila : filas) {
            liquidaciones.add(aLiquidacion((Object[]) fila));
        }
        return liquidaciones;
    }

    @Override
    public Optional<Liquidacion> buscar(Long idLiquidacion) {
        List<?> filas = em.createNativeQuery("SELECT " + COLUMNAS_LIQUIDACION
                        + " FROM liquidacion WHERE id_liquidacion = :id")
                .setParameter("id", idLiquidacion)
                .setMaxResults(1)
                .getResultList();
        return filas.isEmpty() ? Optional.empty() : Optional.of(aLiquidacion((Object[]) filas.get(0)));
    }

    @Override
    public boolean esPropietarioDeLiquidacion(Long idLiquidacion, Long idUsuario) {
        List<?> filas = em.createNativeQuery("SELECT l.id_liquidacion FROM liquidacion l "
                        + "JOIN propietario p ON p.id_propietario = l.id_propietario "
                        + "WHERE l.id_liquidacion = :id AND p.id_usuario = :idUsuario")
                .setParameter("id", idLiquidacion)
                .setParameter("idUsuario", idUsuario.intValue())
                .setMaxResults(1)
                .getResultList();
        return !filas.isEmpty();
    }

    @Override
    public List<DetalleLiquidacion> detallesDe(Long idLiquidacion) {
        List<?> filas = em.createNativeQuery(
                        "SELECT id_reserva, monto_reserva, porcentaje_comision, monto_comision, monto_neto "
                                + "FROM detalle_liquidacion WHERE id_liquidacion = :id ORDER BY id_reserva")
                .setParameter("id", idLiquidacion)
                .getResultList();

        List<DetalleLiquidacion> detalles = new ArrayList<>(filas.size());
        for (Object fila : filas) {
            Object[] f = (Object[]) fila;
            detalles.add(new DetalleLiquidacion(
                    ((Number) f[0]).longValue(),
                    (BigDecimal) f[1],
                    (BigDecimal) f[2],
                    (BigDecimal) f[3],
                    (BigDecimal) f[4]));
        }
        return detalles;
    }

    private static Liquidacion aLiquidacion(Object[] f) {
        return new Liquidacion(
                ((Number) f[0]).longValue(),
                ((Number) f[1]).longValue(),
                LocalDate.parse(String.valueOf(f[2]).trim()),
                LocalDate.parse(String.valueOf(f[3]).trim()),
                (BigDecimal) f[4],
                (BigDecimal) f[5],
                (BigDecimal) f[6],
                Liquidacion.Estado.valueOf((String) f[7]));
    }

    /** `LAST_INSERT_ID()` es de sesion; dentro de la transaccion es el INSERT anterior. */
    private Long ultimoIdGenerado() {
        return ((Number) em.createNativeQuery("SELECT LAST_INSERT_ID()").getSingleResult()).longValue();
    }
}
