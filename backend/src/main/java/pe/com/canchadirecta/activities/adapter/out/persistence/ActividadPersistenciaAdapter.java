package pe.com.canchadirecta.activities.adapter.out.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import pe.com.canchadirecta.activities.application.dto.InscripcionVista;
import pe.com.canchadirecta.activities.application.port.out.ActividadRepositorio;
import pe.com.canchadirecta.activities.domain.model.Actividad;
import pe.com.canchadirecta.activities.domain.model.Inscripcion;
import pe.com.canchadirecta.shared.adapter.out.persistence.SqlUtc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Persistencia de actividades e inscripciones con SQL nativo (ADR-0012). Las fechas se
 * escriben como literal UTC explicito (ADR-0014).
 */
@ApplicationScoped
public class ActividadPersistenciaAdapter implements ActividadRepositorio {

    private static final String COLUMNAS_ACTIVIDAD =
            "a.id_actividad, a.id_establecimiento, e.nombre, d.nombre, a.nombre, a.descripcion, "
                    + "CAST(a.fecha_inicio AS CHAR), CAST(a.fecha_fin AS CHAR), a.cupos, a.precio, "
                    + "a.estado, (SELECT COUNT(*) FROM actividad_inscripcion i "
                    + "WHERE i.id_actividad = a.id_actividad AND i.estado = 'CONFIRMADA')";

    private static final String DESDE_ACTIVIDAD =
            " FROM actividad a JOIN establecimiento e ON e.id_establecimiento = a.id_establecimiento "
                    + "JOIN distrito d ON d.id_distrito = e.id_distrito ";

    private final EntityManager em;

    @Inject
    public ActividadPersistenciaAdapter(EntityManager em) {
        this.em = em;
    }

    @Override
    public List<Actividad> listarPublicas(Instant desde, int limite) {
        List<?> filas = em.createNativeQuery("SELECT " + COLUMNAS_ACTIVIDAD + DESDE_ACTIVIDAD
                        + "WHERE a.estado = 'ACTIVA' AND a.fecha_fin > :desde "
                        + "ORDER BY a.fecha_inicio, a.id_actividad")
                .setParameter("desde", SqlUtc.aLiteral(desde))
                .setMaxResults(limitar(limite))
                .getResultList();
        return aActividades(filas);
    }

    @Override
    public Optional<Actividad> buscar(Long idActividad) {
        List<?> filas = em.createNativeQuery("SELECT " + COLUMNAS_ACTIVIDAD + DESDE_ACTIVIDAD
                        + "WHERE a.id_actividad = :id")
                .setParameter("id", idActividad)
                .setMaxResults(1)
                .getResultList();
        return filas.isEmpty() ? Optional.empty() : Optional.of(aActividad((Object[]) filas.get(0)));
    }

    @Override
    public List<Actividad> listarPorEstablecimiento(Long idEstablecimiento) {
        List<?> filas = em.createNativeQuery("SELECT " + COLUMNAS_ACTIVIDAD + DESDE_ACTIVIDAD
                        + "WHERE a.id_establecimiento = :idEstablecimiento "
                        + "ORDER BY a.fecha_inicio DESC, a.id_actividad DESC")
                .setParameter("idEstablecimiento", idEstablecimiento)
                .getResultList();
        return aActividades(filas);
    }

    @Override
    public List<Actividad> listarTodas(int limite) {
        List<?> filas = em.createNativeQuery("SELECT " + COLUMNAS_ACTIVIDAD + DESDE_ACTIVIDAD
                        + "ORDER BY a.fecha_inicio DESC, a.id_actividad DESC")
                .setMaxResults(Math.max(1, Math.min(limite, 200)))
                .getResultList();
        return aActividades(filas);
    }

    @Override
    @Transactional
    public Long crear(Actividad actividad) {
        em.createNativeQuery("INSERT INTO actividad (id_establecimiento, nombre, descripcion, "
                        + "fecha_inicio, fecha_fin, cupos, precio, estado, fecha_registro) "
                        + "VALUES (:idEstablecimiento, :nombre, :descripcion, :inicio, :fin, :cupos, "
                        + ":precio, :estado, :fechaRegistro)")
                .setParameter("idEstablecimiento", actividad.idEstablecimiento())
                .setParameter("nombre", actividad.nombre())
                .setParameter("descripcion", actividad.descripcion())
                .setParameter("inicio", SqlUtc.aLiteral(actividad.inicio()))
                .setParameter("fin", SqlUtc.aLiteral(actividad.fin()))
                .setParameter("cupos", actividad.cupos())
                .setParameter("precio", actividad.precio())
                .setParameter("estado", actividad.estado().name())
                .setParameter("fechaRegistro", SqlUtc.aLiteral(Instant.now()))
                .executeUpdate();
        return ((Number) em.createNativeQuery("SELECT LAST_INSERT_ID()").getSingleResult()).longValue();
    }

    @Override
    @Transactional
    public boolean actualizar(Actividad actividad) {
        return em.createNativeQuery("UPDATE actividad SET nombre = :nombre, descripcion = :descripcion, "
                        + "fecha_inicio = :inicio, fecha_fin = :fin, cupos = :cupos, precio = :precio "
                        + "WHERE id_actividad = :id AND estado = 'ACTIVA'")
                .setParameter("nombre", actividad.nombre())
                .setParameter("descripcion", actividad.descripcion())
                .setParameter("inicio", SqlUtc.aLiteral(actividad.inicio()))
                .setParameter("fin", SqlUtc.aLiteral(actividad.fin()))
                .setParameter("cupos", actividad.cupos())
                .setParameter("precio", actividad.precio())
                .setParameter("id", actividad.id())
                .executeUpdate() > 0;
    }

    @Override
    @Transactional
    public boolean cancelar(Long idActividad) {
        int cambiadas = em.createNativeQuery("UPDATE actividad SET estado = 'CANCELADA' "
                        + "WHERE id_actividad = :id AND estado = 'ACTIVA'")
                .setParameter("id", idActividad)
                .executeUpdate();
        if (cambiadas == 0) {
            return false;
        }
        em.createNativeQuery("UPDATE actividad_inscripcion SET estado = 'CANCELADA' "
                        + "WHERE id_actividad = :id AND estado = 'CONFIRMADA'")
                .setParameter("id", idActividad)
                .executeUpdate();
        return true;
    }

    @Override
    public boolean esPropietarioDe(Long idEstablecimiento, Long idUsuario) {
        List<?> filas = em.createNativeQuery("SELECT e.id_establecimiento FROM establecimiento e "
                        + "JOIN propietario p ON p.id_propietario = e.id_propietario "
                        + "WHERE e.id_establecimiento = :idEstablecimiento AND p.id_usuario = :idUsuario")
                .setParameter("idEstablecimiento", idEstablecimiento)
                .setParameter("idUsuario", idUsuario)
                .setMaxResults(1)
                .getResultList();
        return !filas.isEmpty();
    }

    @Override
    public Optional<Inscripcion> buscarInscripcion(Long idActividad, Long idUsuario) {
        List<?> filas = em.createNativeQuery("SELECT id_inscripcion, id_actividad, id_usuario, estado, "
                        + "CAST(fecha_registro AS CHAR) FROM actividad_inscripcion "
                        + "WHERE id_actividad = :idActividad AND id_usuario = :idUsuario")
                .setParameter("idActividad", idActividad)
                .setParameter("idUsuario", idUsuario)
                .setMaxResults(1)
                .getResultList();
        return filas.isEmpty() ? Optional.empty() : Optional.of(aInscripcion((Object[]) filas.get(0)));
    }

    @Override
    public long contarInscritos(Long idActividad) {
        return ((Number) em.createNativeQuery("SELECT COUNT(*) FROM actividad_inscripcion "
                        + "WHERE id_actividad = :id AND estado = 'CONFIRMADA'")
                .setParameter("id", idActividad)
                .getSingleResult()).longValue();
    }

    @Override
    @Transactional
    public Optional<Inscripcion> crearInscripcion(Long idActividad, Long idUsuario, Instant ahora) {
        Optional<Integer> cupos = bloquearActividadVigente(idActividad, ahora);
        if (cupos.isEmpty() || contarInscritos(idActividad) >= cupos.get()) {
            return Optional.empty();
        }
        em.createNativeQuery("INSERT INTO actividad_inscripcion "
                        + "(id_actividad, id_usuario, estado, fecha_registro) "
                        + "VALUES (:idActividad, :idUsuario, 'CONFIRMADA', :fecha)")
                .setParameter("idActividad", idActividad)
                .setParameter("idUsuario", idUsuario)
                .setParameter("fecha", SqlUtc.aLiteral(ahora))
                .executeUpdate();
        Long id = ((Number) em.createNativeQuery("SELECT LAST_INSERT_ID()").getSingleResult()).longValue();
        return Optional.of(new Inscripcion(id, idActividad, idUsuario,
                Inscripcion.Estado.CONFIRMADA, ahora));
    }

    @Override
    @Transactional
    public Optional<Inscripcion> reactivarInscripcion(Long idActividad, Long idUsuario, Instant ahora) {
        Optional<Integer> cupos = bloquearActividadVigente(idActividad, ahora);
        if (cupos.isEmpty() || contarInscritos(idActividad) >= cupos.get()) {
            return Optional.empty();
        }
        int cambiadas = em.createNativeQuery("UPDATE actividad_inscripcion "
                        + "SET estado = 'CONFIRMADA', fecha_registro = :fecha "
                        + "WHERE id_actividad = :idActividad AND id_usuario = :idUsuario "
                        + "AND estado = 'CANCELADA'")
                .setParameter("fecha", SqlUtc.aLiteral(ahora))
                .setParameter("idActividad", idActividad)
                .setParameter("idUsuario", idUsuario)
                .executeUpdate();
        if (cambiadas == 0) {
            return Optional.empty();
        }
        return buscarInscripcion(idActividad, idUsuario);
    }

    @Override
    @Transactional
    public boolean cancelarInscripcion(Long idActividad, Long idUsuario) {
        return em.createNativeQuery("UPDATE actividad_inscripcion SET estado = 'CANCELADA' "
                        + "WHERE id_actividad = :idActividad AND id_usuario = :idUsuario "
                        + "AND estado = 'CONFIRMADA'")
                .setParameter("idActividad", idActividad)
                .setParameter("idUsuario", idUsuario)
                .executeUpdate() > 0;
    }

    @Override
    public List<InscripcionVista> listarInscripcionesDeUsuario(Long idUsuario) {
        List<?> filas = em.createNativeQuery("SELECT i.id_inscripcion, i.id_actividad, i.id_usuario, "
                        + "i.estado, CAST(i.fecha_registro AS CHAR), " + COLUMNAS_ACTIVIDAD
                        + " FROM actividad_inscripcion i JOIN actividad a ON a.id_actividad = i.id_actividad "
                        + "JOIN establecimiento e ON e.id_establecimiento = a.id_establecimiento "
                        + "JOIN distrito d ON d.id_distrito = e.id_distrito "
                        + "WHERE i.id_usuario = :idUsuario ORDER BY a.fecha_inicio DESC, i.id_inscripcion DESC")
                .setParameter("idUsuario", idUsuario)
                .getResultList();
        List<InscripcionVista> inscripciones = new ArrayList<>(filas.size());
        for (Object fila : filas) {
            Object[] f = (Object[]) fila;
            Inscripcion inscripcion = new Inscripcion(
                    ((Number) f[0]).longValue(),
                    ((Number) f[1]).longValue(),
                    ((Number) f[2]).longValue(),
                    Inscripcion.Estado.valueOf((String) f[3]),
                    SqlUtc.aInstant(f[4]));
            inscripciones.add(new InscripcionVista(inscripcion, aActividad(f, 5), null, null));
        }
        return inscripciones;
    }

    @Override
    public List<InscripcionVista> listarInscripcionesDeActividad(Long idActividad) {
        List<?> filas = em.createNativeQuery("SELECT i.id_inscripcion, i.id_actividad, i.id_usuario, "
                        + "i.estado, CAST(i.fecha_registro AS CHAR), "
                        + "CONCAT(u.nombres, ' ', u.apellidos), u.correo, " + COLUMNAS_ACTIVIDAD
                        + " FROM actividad_inscripcion i JOIN usuario u ON u.id_usuario = i.id_usuario "
                        + "JOIN actividad a ON a.id_actividad = i.id_actividad "
                        + "JOIN establecimiento e ON e.id_establecimiento = a.id_establecimiento "
                        + "JOIN distrito d ON d.id_distrito = e.id_distrito "
                        + "WHERE i.id_actividad = :idActividad AND i.estado = 'CONFIRMADA' "
                        + "ORDER BY i.fecha_registro, i.id_inscripcion")
                .setParameter("idActividad", idActividad)
                .getResultList();
        List<InscripcionVista> inscripciones = new ArrayList<>(filas.size());
        for (Object fila : filas) {
            Object[] f = (Object[]) fila;
            Inscripcion inscripcion = new Inscripcion(
                    ((Number) f[0]).longValue(),
                    ((Number) f[1]).longValue(),
                    ((Number) f[2]).longValue(),
                    Inscripcion.Estado.valueOf((String) f[3]),
                    SqlUtc.aInstant(f[4]));
            inscripciones.add(new InscripcionVista(inscripcion, aActividad(f, 7), (String) f[5], (String) f[6]));
        }
        return inscripciones;
    }

    private Optional<Integer> bloquearActividadVigente(Long idActividad, Instant ahora) {
        List<?> filas = em.createNativeQuery("SELECT cupos FROM actividad WHERE id_actividad = :id "
                        + "AND estado = 'ACTIVA' AND fecha_fin > :ahora FOR UPDATE")
                .setParameter("id", idActividad)
                .setParameter("ahora", SqlUtc.aLiteral(ahora))
                .getResultList();
        if (filas.isEmpty()) {
            return Optional.empty();
        }
        Object fila = filas.get(0);
        Object valor = fila instanceof Object[] filaArreglo ? filaArreglo[0] : fila;
        return Optional.of(((Number) valor).intValue());
    }

    private static List<Actividad> aActividades(List<?> filas) {
        List<Actividad> actividades = new ArrayList<>(filas.size());
        for (Object fila : filas) {
            actividades.add(aActividad((Object[]) fila));
        }
        return actividades;
    }

    private static Actividad aActividad(Object[] f) {
        return aActividad(f, 0);
    }

    private static Actividad aActividad(Object[] f, int offset) {
        return new Actividad(
                ((Number) f[offset]).longValue(),
                ((Number) f[offset + 1]).longValue(),
                (String) f[offset + 2],
                (String) f[offset + 3],
                (String) f[offset + 4],
                (String) f[offset + 5],
                SqlUtc.aInstant(f[offset + 6]),
                SqlUtc.aInstant(f[offset + 7]),
                ((Number) f[offset + 8]).intValue(),
                (BigDecimal) f[offset + 9],
                Actividad.Estado.valueOf((String) f[offset + 10]),
                ((Number) f[offset + 11]).longValue());
    }

    private static Inscripcion aInscripcion(Object[] f) {
        return new Inscripcion(
                ((Number) f[0]).longValue(),
                ((Number) f[1]).longValue(),
                ((Number) f[2]).longValue(),
                Inscripcion.Estado.valueOf((String) f[3]),
                SqlUtc.aInstant(f[4]));
    }

    private static int limitar(int limite) {
        return Math.max(1, Math.min(limite, 100));
    }
}
