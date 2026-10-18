package pe.com.canchadirecta.dashboard.adapter.out.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import pe.com.canchadirecta.dashboard.application.port.out.MetricasRepositorio;
import pe.com.canchadirecta.dashboard.domain.model.MetricasEstablecimiento;
import pe.com.canchadirecta.shared.adapter.out.persistence.SqlUtc;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Agregados de reservas e ingresos por establecimiento con SQL nativo (ADR-0012). La ocupacion
 * estima una jornada de 12 horas por cancha activa y dia.
 */
@ApplicationScoped
public class MetricasPersistenciaAdapter implements MetricasRepositorio {

    private static final ZoneId ZONA_NEGOCIO = ZoneId.of("America/Lima");
    private static final int HORAS_JORNADA = 12;

    /** Monto cobrado de la reserva, incluida la cochera cuando existe. */
    private static final String MONTO =
            "(r.monto_cancha - r.monto_descuento + COALESCE((SELECT SUM(rc.monto) "
                    + "FROM reserva_cochera rc WHERE rc.id_reserva = r.id_reserva), 0))";

    private final EntityManager em;

    @Inject
    public MetricasPersistenciaAdapter(EntityManager em) {
        this.em = em;
    }

    @Override
    public boolean esPropietarioDe(Long idEstablecimiento, Long idUsuario) {
        return !em.createNativeQuery("SELECT e.id_establecimiento FROM establecimiento e "
                        + "JOIN propietario p ON p.id_propietario = e.id_propietario "
                        + "WHERE e.id_establecimiento = :idEstablecimiento AND p.id_usuario = :idUsuario")
                .setParameter("idEstablecimiento", idEstablecimiento)
                .setParameter("idUsuario", idUsuario)
                .setMaxResults(1)
                .getResultList().isEmpty();
    }

    @Override
    public Optional<MetricasEstablecimiento> metricas(Long idEstablecimiento, Instant desde, Instant hasta,
                                                      Instant ahora, int dias) {
        int canchasActivas = canchasActivas(idEstablecimiento);
        Object[] fila = (Object[]) em.createNativeQuery("SELECT COUNT(*), "
                        + "COALESCE(SUM(CASE WHEN r.estado = 'CONFIRMADA' THEN 1 ELSE 0 END), 0), "
                        + "COALESCE(SUM(CASE WHEN r.estado = 'PENDIENTE' THEN 1 ELSE 0 END), 0), "
                        + "COALESCE(SUM(CASE WHEN r.estado IN ('CANCELADA','RECHAZADA','EXPIRADA') "
                        + "THEN 1 ELSE 0 END), 0), "
                        + "COALESCE(SUM(CASE WHEN r.estado IN ('CONFIRMADA','FINALIZADA') "
                        + "THEN " + MONTO + " ELSE 0 END), 0), "
                        + "COALESCE(SUM(CASE WHEN r.estado IN ('PENDIENTE','CONFIRMADA','FINALIZADA') "
                        + "AND r.inicio >= :desde AND r.inicio < :hasta "
                        + "THEN TIMESTAMPDIFF(MINUTE, r.inicio, r.fin) ELSE 0 END), 0) / 60 "
                        + "FROM reserva r JOIN cancha c ON c.id_cancha = r.id_cancha "
                        + "WHERE c.id_establecimiento = :idEstablecimiento "
                        + "AND r.fecha_creacion >= :desde AND r.fecha_creacion < :hasta")
                .setParameter("idEstablecimiento", idEstablecimiento)
                .setParameter("desde", SqlUtc.aLiteral(desde))
                .setParameter("hasta", SqlUtc.aLiteral(hasta))
                .getSingleResult();

        long total = ((Number) fila[0]).longValue();
        long confirmadas = ((Number) fila[1]).longValue();
        long pendientes = ((Number) fila[2]).longValue();
        long canceladas = ((Number) fila[3]).longValue();
        BigDecimal ingresos = new BigDecimal(fila[4].toString()).setScale(2, RoundingMode.HALF_UP);
        double horas = ((Number) fila[5]).doubleValue();
        double ocupacion = canchasActivas == 0 ? 0
                : Math.min(100.0, horas * 100.0 / (canchasActivas * dias * HORAS_JORNADA));

        MetricasEstablecimiento.Resumen resumen = new MetricasEstablecimiento.Resumen(
                total, confirmadas, pendientes, canceladas, ingresos,
                redondear(horas), redondear(ocupacion), canchasActivas);

        return Optional.of(new MetricasEstablecimiento(idEstablecimiento, dias, desde, hasta, resumen,
                serie(idEstablecimiento, desde, hasta, dias),
                canchas(idEstablecimiento, desde, hasta),
                proximas(idEstablecimiento, ahora)));
    }

    private int canchasActivas(Long idEstablecimiento) {
        return ((Number) em.createNativeQuery("SELECT COUNT(*) FROM cancha "
                        + "WHERE id_establecimiento = :id AND estado = 'ACTIVA'")
                .setParameter("id", idEstablecimiento)
                .getSingleResult()).intValue();
    }

    private List<MetricasEstablecimiento.PuntoDia> serie(Long idEstablecimiento, Instant desde,
                                                         Instant hasta, int dias) {
        Map<LocalDate, MetricasEstablecimiento.PuntoDia> porFecha = new LinkedHashMap<>();
        List<?> filas = em.createNativeQuery("SELECT DATE(r.fecha_creacion), COUNT(*), "
                        + "COALESCE(SUM(CASE WHEN r.estado IN ('CONFIRMADA','FINALIZADA') "
                        + "THEN " + MONTO + " ELSE 0 END), 0) "
                        + "FROM reserva r JOIN cancha c ON c.id_cancha = r.id_cancha "
                        + "WHERE c.id_establecimiento = :id "
                        + "AND r.fecha_creacion >= :desde AND r.fecha_creacion < :hasta "
                        + "GROUP BY DATE(r.fecha_creacion) ORDER BY DATE(r.fecha_creacion)")
                .setParameter("id", idEstablecimiento)
                .setParameter("desde", SqlUtc.aLiteral(desde))
                .setParameter("hasta", SqlUtc.aLiteral(hasta))
                .getResultList();
        for (Object fila : filas) {
            Object[] f = (Object[]) fila;
            LocalDate fecha = LocalDate.parse(f[0].toString());
            porFecha.put(fecha, new MetricasEstablecimiento.PuntoDia(
                    fecha,
                    ((Number) f[1]).longValue(),
                    new BigDecimal(f[2].toString()).setScale(2, RoundingMode.HALF_UP)));
        }
        LocalDate inicio = LocalDate.ofInstant(desde, ZONA_NEGOCIO);
        List<MetricasEstablecimiento.PuntoDia> serie = new ArrayList<>(dias);
        for (int i = 0; i < dias; i++) {
            LocalDate fecha = inicio.plusDays(i);
            serie.add(porFecha.getOrDefault(fecha,
                    new MetricasEstablecimiento.PuntoDia(fecha, 0, BigDecimal.ZERO.setScale(2))));
        }
        return serie;
    }

    private List<MetricasEstablecimiento.CanchaMetrica> canchas(Long idEstablecimiento,
                                                                Instant desde, Instant hasta) {
        List<?> filas = em.createNativeQuery("SELECT c.id_cancha, c.nombre, COUNT(*), "
                        + "COALESCE(SUM(CASE WHEN r.estado IN ('CONFIRMADA','FINALIZADA') "
                        + "THEN " + MONTO + " ELSE 0 END), 0) "
                        + "FROM reserva r JOIN cancha c ON c.id_cancha = r.id_cancha "
                        + "WHERE c.id_establecimiento = :id "
                        + "AND r.fecha_creacion >= :desde AND r.fecha_creacion < :hasta "
                        + "GROUP BY c.id_cancha, c.nombre ORDER BY COUNT(*) DESC, 4 DESC LIMIT 5")
                .setParameter("id", idEstablecimiento)
                .setParameter("desde", SqlUtc.aLiteral(desde))
                .setParameter("hasta", SqlUtc.aLiteral(hasta))
                .getResultList();
        List<MetricasEstablecimiento.CanchaMetrica> canchas = new ArrayList<>(filas.size());
        for (Object fila : filas) {
            Object[] f = (Object[]) fila;
            canchas.add(new MetricasEstablecimiento.CanchaMetrica(
                    ((Number) f[0]).longValue(), (String) f[1], ((Number) f[2]).longValue(),
                    new BigDecimal(f[3].toString()).setScale(2, RoundingMode.HALF_UP)));
        }
        return canchas;
    }

    private List<MetricasEstablecimiento.ReservaProxima> proximas(Long idEstablecimiento, Instant ahora) {
        List<?> filas = em.createNativeQuery("SELECT r.id_reserva, CONCAT(u.nombres, ' ', u.apellidos), "
                        + "c.nombre, CAST(r.inicio AS CHAR), CAST(r.fin AS CHAR), r.estado, " + MONTO
                        + " FROM reserva r JOIN cancha c ON c.id_cancha = r.id_cancha "
                        + "JOIN usuario u ON u.id_usuario = r.id_usuario "
                        + "WHERE c.id_establecimiento = :id AND r.inicio >= :ahora "
                        + "AND r.estado IN ('PENDIENTE','CONFIRMADA') "
                        + "ORDER BY r.inicio, r.id_reserva LIMIT 8")
                .setParameter("id", idEstablecimiento)
                .setParameter("ahora", SqlUtc.aLiteral(ahora))
                .getResultList();
        List<MetricasEstablecimiento.ReservaProxima> proximas = new ArrayList<>(filas.size());
        for (Object fila : filas) {
            Object[] f = (Object[]) fila;
            proximas.add(new MetricasEstablecimiento.ReservaProxima(
                    ((Number) f[0]).longValue(), (String) f[1], (String) f[2],
                    SqlUtc.aInstant(f[3]), SqlUtc.aInstant(f[4]), (String) f[5],
                    new BigDecimal(f[6].toString()).setScale(2, RoundingMode.HALF_UP)));
        }
        return proximas;
    }

    private static double redondear(double valor) {
        return BigDecimal.valueOf(valor).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}
