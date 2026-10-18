package pe.com.canchadirecta.engagement.adapter.out.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import pe.com.canchadirecta.engagement.application.port.out.NotificacionRepositorio;
import pe.com.canchadirecta.engagement.domain.model.Notificacion;
import pe.com.canchadirecta.shared.adapter.out.persistence.SqlUtc;
import pe.com.canchadirecta.shared.application.port.out.AvisosUsuario;
import pe.com.canchadirecta.shared.application.port.out.Reloj;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Persistencia de notificaciones in-app (SQL nativo, ADR-0012) y adaptador del puerto
 * compartido {@link AvisosUsuario} que usan reservas y pagos para dejar constancia de sus
 * eventos.
 */
@ApplicationScoped
public class NotificacionPersistenciaAdapter implements NotificacionRepositorio, AvisosUsuario {

    private static final String COLUMNAS =
            "id_notificacion, id_usuario, titulo, mensaje, leida, CAST(fecha_envio AS CHAR)";

    private final EntityManager em;
    private final Reloj reloj;

    @Inject
    public NotificacionPersistenciaAdapter(EntityManager em, Reloj reloj) {
        this.em = em;
        this.reloj = reloj;
    }

    @Override
    public void avisar(Long idUsuario, String titulo, String mensaje) {
        registrar(idUsuario, recortar(titulo, Notificacion.TITULO_MAX),
                recortar(mensaje, Notificacion.MENSAJE_MAX), reloj.ahora());
    }

    @Override
    @Transactional
    public void registrar(Long idUsuario, String titulo, String mensaje, Instant fechaEnvio) {
        // Literal UTC explicito (ADR-0014); el `DEFAULT CURRENT_TIMESTAMP` seria hora local (L-04).
        em.createNativeQuery("INSERT INTO notificacion (id_usuario, titulo, mensaje, leida, fecha_envio) "
                        + "VALUES (:idUsuario, :titulo, :mensaje, 0, :fecha)")
                .setParameter("idUsuario", idUsuario)
                .setParameter("titulo", titulo)
                .setParameter("mensaje", mensaje)
                .setParameter("fecha", aUtc(fechaEnvio))
                .executeUpdate();
    }

    @Override
    public List<Notificacion> listarPorUsuario(Long idUsuario, int limite) {
        List<?> filas = em.createNativeQuery("SELECT " + COLUMNAS + " FROM notificacion "
                        + "WHERE id_usuario = :idUsuario ORDER BY fecha_envio DESC, id_notificacion DESC")
                .setParameter("idUsuario", idUsuario)
                .setMaxResults(Math.max(1, Math.min(limite, 100)))
                .getResultList();
        List<Notificacion> notificaciones = new ArrayList<>(filas.size());
        for (Object fila : filas) {
            Object[] f = (Object[]) fila;
            notificaciones.add(new Notificacion(
                    ((Number) f[0]).longValue(),
                    ((Number) f[1]).longValue(),
                    (String) f[2],
                    (String) f[3],
                    leida(f[4]),
                    SqlUtc.aInstant(f[5])));
        }
        return notificaciones;
    }

    @Override
    public long contarNoLeidas(Long idUsuario) {
        return ((Number) em.createNativeQuery("SELECT COUNT(*) FROM notificacion "
                        + "WHERE id_usuario = :idUsuario AND leida = 0")
                .setParameter("idUsuario", idUsuario)
                .getSingleResult()).longValue();
    }

    @Override
    @Transactional
    public boolean marcarLeida(Long idNotificacion, Long idUsuario) {
        int filas = em.createNativeQuery("UPDATE notificacion SET leida = 1 "
                        + "WHERE id_notificacion = :id AND id_usuario = :idUsuario AND leida = 0")
                .setParameter("id", idNotificacion)
                .setParameter("idUsuario", idUsuario)
                .executeUpdate();
        if (filas > 0) {
            return true;
        }
        // Ya estaba leida: se comprueba que exista y sea del usuario para no dar 404.
        return !em.createNativeQuery("SELECT 1 FROM notificacion "
                        + "WHERE id_notificacion = :id AND id_usuario = :idUsuario")
                .setParameter("id", idNotificacion)
                .setParameter("idUsuario", idUsuario)
                .setMaxResults(1)
                .getResultList().isEmpty();
    }

    /** MySQL mapea `TINYINT(1)` a `Boolean` o a `Number` segun el driver; se aceptan ambos. */
    private static boolean leida(Object valor) {
        if (valor instanceof Boolean booleano) {
            return booleano;
        }
        return valor != null && ((Number) valor).intValue() != 0;
    }

    private static String recortar(String texto, int maximo) {        if (texto == null) {
            return "";
        }
        return texto.length() <= maximo ? texto : texto.substring(0, maximo);
    }

    private static LocalDateTime aUtc(Instant instante) {
        return LocalDateTime.ofInstant(instante, ZoneOffset.UTC);
    }
}
