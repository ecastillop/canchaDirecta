package pe.com.canchadirecta.engagement.adapter.out.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import pe.com.canchadirecta.engagement.application.dto.ResumenCalificaciones;
import pe.com.canchadirecta.engagement.application.port.out.CalificacionRepositorio;
import pe.com.canchadirecta.engagement.domain.model.Calificacion;
import pe.com.canchadirecta.shared.adapter.out.persistence.SqlUtc;
import pe.com.canchadirecta.shared.domain.DomainException;
import pe.com.canchadirecta.shared.domain.ErrorCodes;

import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Persistencia de calificaciones con SQL nativo (ADR-0012).
 *
 * <p>La tabla `calificacion` cuelga de `reserva`, no de `cancha`; el listado y el promedio
 * de una cancha se obtienen con `JOIN reserva` (por eso RN-04, una por reserva, tambien
 * acota a una por cancha y reserva).</p>
 */
@ApplicationScoped
public class CalificacionPersistenciaAdapter implements CalificacionRepositorio {

    /** Columnas de `calificacion` en el orden que espera {@link #aCalificacion(Object[])}. */
    private static final String COLUMNAS =
            "c.id_calificacion, c.id_reserva, c.puntaje, c.comentario, CAST(c.fecha_registro AS CHAR)";

    private final EntityManager em;

    @Inject
    public CalificacionPersistenciaAdapter(EntityManager em) {
        this.em = em;
    }

    @Override
    public boolean existeParaReserva(Long idReserva) {
        List<?> filas = em.createNativeQuery("SELECT 1 FROM calificacion WHERE id_reserva = :id")
                .setParameter("id", idReserva)
                .setMaxResults(1)
                .getResultList();
        return !filas.isEmpty();
    }

    @Override
    @Transactional
    public Calificacion registrar(Calificacion calificacion) {
        // Se escribe el instante como literal UTC explicito (ADR-0014): la columna tiene
        // `DEFAULT CURRENT_TIMESTAMP`, que seria la hora local del servidor (L-04), y no
        // volveria a leerse como el mismo instante.
        try {
            em.createNativeQuery("INSERT INTO calificacion "
                            + "(id_reserva, puntaje, comentario, fecha_registro) "
                            + "VALUES (:idReserva, :puntaje, :comentario, :fecha)")
                    .setParameter("idReserva", calificacion.idReserva())
                    .setParameter("puntaje", calificacion.puntaje())
                    .setParameter("comentario", calificacion.comentario())
                    .setParameter("fecha", aUtc(calificacion.fechaRegistro()))
                    .executeUpdate();
        } catch (PersistenceException ex) {
            // Dos calificaciones concurrentes de la misma reserva: la segunda choca con
            // `uq_calificacion_reserva`. Se traduce a 409 en lugar de dejar un 500.
            if (esViolacionDeUnicidad(ex)) {
                throw new DomainException(ErrorCodes.CONFLICTO, "La reserva ya fue calificada.");
            }
            throw ex;
        }

        Long id = ((Number) em.createNativeQuery("SELECT LAST_INSERT_ID()").getSingleResult()).longValue();
        return new Calificacion(id, calificacion.idReserva(), calificacion.puntaje(),
                calificacion.comentario(), calificacion.fechaRegistro());
    }

    @Override
    public ResumenCalificaciones resumenDeCancha(Long idCancha, int limite) {
        List<?> filas = em.createNativeQuery("SELECT " + COLUMNAS + " FROM calificacion c "
                        + "JOIN reserva r ON r.id_reserva = c.id_reserva "
                        + "WHERE r.id_cancha = :idCancha ORDER BY c.fecha_registro DESC")
                .setParameter("idCancha", idCancha.intValue())
                .setMaxResults(Math.max(1, Math.min(limite, 100)))
                .getResultList();

        List<Calificacion> calificaciones = new ArrayList<>(filas.size());
        for (Object fila : filas) {
            calificaciones.add(aCalificacion((Object[]) fila));
        }

        Object[] agregado = (Object[]) em.createNativeQuery("SELECT COUNT(*), AVG(c.puntaje) "
                        + "FROM calificacion c JOIN reserva r ON r.id_reserva = c.id_reserva "
                        + "WHERE r.id_cancha = :idCancha")
                .setParameter("idCancha", idCancha.intValue())
                .getSingleResult();
        long total = ((Number) agregado[0]).longValue();
        double promedio = agregado[1] == null ? 0.0 : ((Number) agregado[1]).doubleValue();

        return new ResumenCalificaciones(idCancha, total, promedio, calificaciones);
    }

    private static Calificacion aCalificacion(Object[] f) {
        return new Calificacion(
                ((Number) f[0]).longValue(),
                ((Number) f[1]).longValue(),
                ((Number) f[2]).intValue(),
                (String) f[3],
                SqlUtc.aInstant(f[4]));
    }

    private static LocalDateTime aUtc(Instant instante) {
        return LocalDateTime.ofInstant(instante, ZoneOffset.UTC);
    }

    private static boolean esViolacionDeUnicidad(Throwable error) {
        Throwable actual = error;
        while (actual != null) {
            if (actual instanceof SQLException sql
                    && "23000".equals(sql.getSQLState())
                    && sql.getMessage() != null
                    && sql.getMessage().contains("uq_calificacion_reserva")) {
                return true;
            }
            actual = actual.getCause();
        }
        return false;
    }
}
