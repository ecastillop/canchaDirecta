package pe.com.canchadirecta.engagement.adapter.out.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import pe.com.canchadirecta.engagement.application.port.out.ReclamoRepositorio;
import pe.com.canchadirecta.engagement.domain.model.Reclamo;
import pe.com.canchadirecta.engagement.domain.model.ReclamoSeguimiento;
import pe.com.canchadirecta.shared.adapter.out.persistence.SqlUtc;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Persistencia de reclamos y su seguimiento con SQL nativo (ADR-0012).
 *
 * <p>Las fechas se escriben como literal UTC explicito (ADR-0014): las columnas tienen
 * `DEFAULT CURRENT_TIMESTAMP`, que seria la hora local del servidor (L-04).</p>
 */
@ApplicationScoped
public class ReclamoPersistenciaAdapter implements ReclamoRepositorio {

    private static final String COLUMNAS =
            "id_reclamo, id_usuario, id_reserva, asunto, descripcion, estado, CAST(fecha_registro AS CHAR)";

    private final EntityManager em;

    @Inject
    public ReclamoPersistenciaAdapter(EntityManager em) {
        this.em = em;
    }

    @Override
    @Transactional
    public Reclamo registrar(Reclamo reclamo) {
        em.createNativeQuery("INSERT INTO reclamo "
                        + "(id_usuario, id_reserva, asunto, descripcion, estado, fecha_registro) "
                        + "VALUES (:idUsuario, :idReserva, :asunto, :descripcion, :estado, :fecha)")
                .setParameter("idUsuario", reclamo.idUsuario())
                .setParameter("idReserva", reclamo.idReserva())
                .setParameter("asunto", reclamo.asunto())
                .setParameter("descripcion", reclamo.descripcion())
                .setParameter("estado", reclamo.estado().name())
                .setParameter("fecha", aUtc(reclamo.fechaRegistro()))
                .executeUpdate();

        Long id = ((Number) em.createNativeQuery("SELECT LAST_INSERT_ID()").getSingleResult()).longValue();
        return new Reclamo(id, reclamo.idUsuario(), reclamo.idReserva(), reclamo.asunto(),
                reclamo.descripcion(), reclamo.estado(), reclamo.fechaRegistro());
    }

    @Override
    public Optional<Reclamo> buscar(Long idReclamo) {
        List<?> filas = em.createNativeQuery("SELECT " + COLUMNAS + " FROM reclamo WHERE id_reclamo = :id")
                .setParameter("id", idReclamo)
                .setMaxResults(1)
                .getResultList();
        return filas.isEmpty() ? Optional.empty() : Optional.of(aReclamo((Object[]) filas.get(0)));
    }

    @Override
    public List<Reclamo> listarPorUsuario(Long idUsuario, int limite) {
        List<?> filas = em.createNativeQuery("SELECT " + COLUMNAS + " FROM reclamo "
                        + "WHERE id_usuario = :idUsuario ORDER BY fecha_registro DESC")
                .setParameter("idUsuario", idUsuario)
                .setMaxResults(Math.max(1, Math.min(limite, 100)))
                .getResultList();
        List<Reclamo> reclamos = new ArrayList<>(filas.size());
        for (Object fila : filas) {
            reclamos.add(aReclamo((Object[]) fila));
        }
        return reclamos;
    }

    @Override
    public List<Reclamo> listar(Reclamo.Estado estado, int limite) {
        String sql = "SELECT " + COLUMNAS + " FROM reclamo";
        if (estado != null) {
            sql += " WHERE estado = :estado";
        }
        sql += " ORDER BY fecha_registro DESC, id_reclamo DESC";
        var query = em.createNativeQuery(sql);
        if (estado != null) {
            query.setParameter("estado", estado.name());
        }
        List<?> filas = query.setMaxResults(Math.max(1, Math.min(limite, 200))).getResultList();
        List<Reclamo> reclamos = new ArrayList<>(filas.size());
        for (Object fila : filas) {
            reclamos.add(aReclamo((Object[]) fila));
        }
        return reclamos;
    }

    @Override
    @Transactional
    public boolean cambiarEstado(Long idReclamo, Reclamo.Estado esperado, Reclamo.Estado nuevo) {
        return em.createNativeQuery("UPDATE reclamo SET estado = :nuevo "
                        + "WHERE id_reclamo = :id AND estado = :esperado")
                .setParameter("nuevo", nuevo.name())
                .setParameter("id", idReclamo)
                .setParameter("esperado", esperado.name())
                .executeUpdate() > 0;
    }

    @Override
    public List<ReclamoSeguimiento> seguimientos(Long idReclamo) {
        List<?> filas = em.createNativeQuery("SELECT id_seguimiento, id_reclamo, id_usuario, "
                        + "comentario, CAST(fecha AS CHAR) FROM reclamo_seguimiento "
                        + "WHERE id_reclamo = :id ORDER BY fecha, id_seguimiento")
                .setParameter("id", idReclamo)
                .getResultList();
        List<ReclamoSeguimiento> seguimientos = new ArrayList<>(filas.size());
        for (Object fila : filas) {
            Object[] f = (Object[]) fila;
            seguimientos.add(new ReclamoSeguimiento(
                    ((Number) f[0]).longValue(),
                    ((Number) f[1]).longValue(),
                    f[2] == null ? null : ((Number) f[2]).longValue(),
                    (String) f[3],
                    SqlUtc.aInstant(f[4])));
        }
        return seguimientos;
    }

    @Override
    @Transactional
    public ReclamoSeguimiento agregarSeguimiento(ReclamoSeguimiento seguimiento) {
        em.createNativeQuery("INSERT INTO reclamo_seguimiento "
                        + "(id_reclamo, id_usuario, comentario, fecha) VALUES (:idReclamo, :idUsuario, :comentario, :fecha)")
                .setParameter("idReclamo", seguimiento.idReclamo())
                .setParameter("idUsuario", seguimiento.idUsuario())
                .setParameter("comentario", seguimiento.comentario())
                .setParameter("fecha", aUtc(seguimiento.fecha()))
                .executeUpdate();

        Long id = ((Number) em.createNativeQuery("SELECT LAST_INSERT_ID()").getSingleResult()).longValue();
        return new ReclamoSeguimiento(id, seguimiento.idReclamo(), seguimiento.idUsuario(),
                seguimiento.comentario(), seguimiento.fecha());
    }

    private static Reclamo aReclamo(Object[] f) {
        return new Reclamo(
                ((Number) f[0]).longValue(),
                ((Number) f[1]).longValue(),
                f[2] == null ? null : ((Number) f[2]).longValue(),
                (String) f[3],
                (String) f[4],
                Reclamo.Estado.valueOf((String) f[5]),
                SqlUtc.aInstant(f[6]));
    }

    private static LocalDateTime aUtc(Instant instante) {
        return LocalDateTime.ofInstant(instante, ZoneOffset.UTC);
    }
}
