package pe.com.canchadirecta.payments.adapter.out.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import pe.com.canchadirecta.payments.application.port.out.PagoRepositorio;
import pe.com.canchadirecta.payments.domain.model.Pago;
import pe.com.canchadirecta.payments.domain.model.Reembolso;
import pe.com.canchadirecta.shared.adapter.out.persistence.SqlUtc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Persistencia de pagos, reembolsos y eventos de pago con SQL nativo (ADR-0012).
 *
 * <p>Las fechas se escriben y leen como literales UTC explicitos ({@link SqlUtc}, ADR-0014)
 * para no depender de la zona de la JVM. Todas las operaciones se unen a la transaccion del
 * caso de uso (propagacion REQUIRED).</p>
 */
@ApplicationScoped
public class PagoPersistenciaAdapter implements PagoRepositorio {

    /** Columnas de `pago` en el orden que espera {@link #aPago(Object[])}. */
    private static final String COLUMNAS_PAGO =
            "id_pago, id_reserva, monto, medio_pago, codigo_operacion, estado, CAST(fecha_pago AS CHAR)";

    /** Longitud de `pago_evento.detalle` (VARCHAR(300)). */
    private static final int DETALLE_MAXIMO = 300;

    private final EntityManager em;

    @Inject
    public PagoPersistenciaAdapter(EntityManager em) {
        this.em = em;
    }

    @Override
    public Optional<Pago> ultimoPagoDe(Long idReserva) {
        return primerPago("SELECT " + COLUMNAS_PAGO + " FROM pago WHERE id_reserva = :id "
                + "ORDER BY id_pago DESC", idReserva);
    }

    @Override
    public Optional<Pago> pagoAprobadoDe(Long idReserva) {
        return primerPago("SELECT " + COLUMNAS_PAGO + " FROM pago WHERE id_reserva = :id "
                + "AND estado = 'APROBADO' ORDER BY id_pago DESC", idReserva);
    }

    private Optional<Pago> primerPago(String sql, Long idReserva) {
        List<?> filas = em.createNativeQuery(sql)
                .setParameter("id", idReserva)
                .setMaxResults(1)
                .getResultList();
        return filas.isEmpty() ? Optional.empty() : Optional.of(aPago((Object[]) filas.get(0)));
    }

    @Override
    @Transactional
    public Long registrarPago(Long idReserva, BigDecimal monto, String medioPago, String codigoOperacion,
                              Pago.Estado estado, Instant momento) {
        em.createNativeQuery("INSERT INTO pago "
                        + "(id_reserva, monto, medio_pago, codigo_operacion, estado, fecha_pago) "
                        + "VALUES (:idReserva, :monto, :medio, :codigo, :estado, :fecha)")
                .setParameter("idReserva", idReserva)
                .setParameter("monto", monto)
                .setParameter("medio", medioPago)
                .setParameter("codigo", codigoOperacion)
                .setParameter("estado", estado.name())
                .setParameter("fecha", SqlUtc.aLiteral(momento))
                .executeUpdate();
        return ultimoIdGenerado();
    }

    @Override
    @Transactional
    public void registrarEvento(Long idReserva, Long idPago, String tipo, String detalle) {
        em.createNativeQuery("INSERT INTO pago_evento (id_pago, id_reserva, tipo, detalle) "
                        + "VALUES (:idPago, :idReserva, :tipo, :detalle)")
                .setParameter("idPago", idPago)
                .setParameter("idReserva", idReserva)
                .setParameter("tipo", tipo)
                .setParameter("detalle", recortar(detalle, DETALLE_MAXIMO))
                .executeUpdate();
    }

    @Override
    @Transactional
    public void marcarReembolsado(Long idPago) {
        em.createNativeQuery("UPDATE pago SET estado = 'REEMBOLSADO' WHERE id_pago = :id")
                .setParameter("id", idPago)
                .executeUpdate();
    }

    @Override
    @Transactional
    public Long registrarReembolso(Long idPago, BigDecimal monto, String motivo, Instant momento) {
        em.createNativeQuery("INSERT INTO reembolso "
                        + "(id_pago, monto, motivo, estado, fecha_solicitud, fecha_resolucion) "
                        + "VALUES (:idPago, :monto, :motivo, 'PROCESADO', :fecha, :fecha)")
                .setParameter("idPago", idPago)
                .setParameter("monto", monto)
                .setParameter("motivo", recortar(motivo, Reembolso.MOTIVO_MAXIMO))
                .setParameter("fecha", SqlUtc.aLiteral(momento))
                .executeUpdate();
        return ultimoIdGenerado();
    }

    @Override
    public List<Reembolso> reembolsosDePago(Long idPago) {
        List<?> filas = em.createNativeQuery(
                        "SELECT id_reembolso, id_pago, monto, motivo, estado, "
                                + "CAST(fecha_solicitud AS CHAR), CAST(fecha_resolucion AS CHAR) "
                                + "FROM reembolso WHERE id_pago = :id ORDER BY id_reembolso DESC")
                .setParameter("id", idPago)
                .getResultList();

        List<Reembolso> reembolsos = new ArrayList<>(filas.size());
        for (Object fila : filas) {
            reembolsos.add(aReembolso((Object[]) fila));
        }
        return reembolsos;
    }

    private static Pago aPago(Object[] f) {
        return new Pago(
                ((Number) f[0]).longValue(),
                ((Number) f[1]).longValue(),
                (BigDecimal) f[2],
                (String) f[3],
                (String) f[4],
                Pago.Estado.valueOf((String) f[5]),
                SqlUtc.aInstant(f[6]));
    }

    private static Reembolso aReembolso(Object[] f) {
        return new Reembolso(
                ((Number) f[0]).longValue(),
                ((Number) f[1]).longValue(),
                (BigDecimal) f[2],
                (String) f[3],
                Reembolso.Estado.valueOf((String) f[4]),
                SqlUtc.aInstant(f[5]),
                f[6] == null ? null : SqlUtc.aInstant(f[6]));
    }

    /** `LAST_INSERT_ID()` es de sesion; dentro de la transaccion es el INSERT anterior. */
    private Long ultimoIdGenerado() {
        return ((Number) em.createNativeQuery("SELECT LAST_INSERT_ID()").getSingleResult()).longValue();
    }

    private static String recortar(String texto, int maximo) {
        if (texto == null) {
            return null;
        }
        return texto.length() <= maximo ? texto : texto.substring(0, maximo);
    }
}
