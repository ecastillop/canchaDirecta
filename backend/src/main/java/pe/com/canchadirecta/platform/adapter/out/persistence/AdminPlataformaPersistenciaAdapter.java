package pe.com.canchadirecta.platform.adapter.out.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import pe.com.canchadirecta.platform.application.port.out.AdminPlataformaRepositorio;
import pe.com.canchadirecta.platform.domain.model.Auditoria;
import pe.com.canchadirecta.platform.domain.model.CocheraAdmin;
import pe.com.canchadirecta.platform.domain.model.PromocionAdmin;
import pe.com.canchadirecta.platform.domain.model.PropietarioAdmin;
import pe.com.canchadirecta.platform.domain.model.ReporteAdmin;
import pe.com.canchadirecta.platform.domain.model.SupervisionAdmin;
import pe.com.canchadirecta.platform.domain.model.UsuarioAdmin;
import pe.com.canchadirecta.shared.adapter.out.persistence.SqlUtc;
import pe.com.canchadirecta.shared.application.port.out.PromocionesCancha;
import pe.com.canchadirecta.shared.application.port.out.RegistroAuditoria;
import pe.com.canchadirecta.shared.application.port.out.Reloj;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Consultas de administracion y registro de auditoria con SQL nativo (ADR-0012).
 *
 * <p>`fecha_evento` se escribe como literal UTC explicito (ADR-0014); el `DEFAULT
 * CURRENT_TIMESTAMP` seria hora local (L-04).</p>
 */
@ApplicationScoped
public class AdminPlataformaPersistenciaAdapter implements AdminPlataformaRepositorio, RegistroAuditoria,
        PromocionesCancha {

    private static final ZoneId ZONA_NEGOCIO = ZoneId.of("America/Lima");

    private static final String PROPIETARIO_COLUMNAS =
            "p.id_propietario, p.id_usuario, CONCAT(u.nombres, ' ', u.apellidos), u.correo, "
                    + "p.documento_identidad, p.estado_validacion";

    private static final String USUARIO_COLUMNAS =
            "SELECT u.id_usuario, CONCAT(u.nombres, ' ', u.apellidos), u.correo, u.telefono, u.estado, "
                    + "CAST(u.fecha_registro AS CHAR), "
                    + "COALESCE(GROUP_CONCAT(r.nombre ORDER BY r.nombre SEPARATOR ','), '') "
                    + "FROM usuario u LEFT JOIN usuario_rol ur ON ur.id_usuario = u.id_usuario "
                    + "LEFT JOIN rol r ON r.id_rol = ur.id_rol";

    private static final String PROMOCION_COLUMNAS =
            "SELECT p.id_promocion, p.codigo, p.descripcion, p.tipo_descuento, p.valor, "
                    + "CAST(p.fecha_inicio AS CHAR), CAST(p.fecha_fin AS CHAR), p.estado, "
                    + "p.limite_usos, p.limite_por_usuario, "
                    + "(SELECT COUNT(*) FROM reserva r WHERE r.id_promocion = p.id_promocion "
                    + "AND r.estado NOT IN ('CANCELADA','RECHAZADA','EXPIRADA')), "
                    + "COALESCE(GROUP_CONCAT(cp.id_cancha ORDER BY cp.id_cancha SEPARATOR ','), '') "
                    + "FROM promocion p LEFT JOIN cancha_promocion cp ON cp.id_promocion = p.id_promocion";

    private static final String COCHERA_COLUMNAS =
            "SELECT c.id_cochera, c.id_establecimiento, e.nombre, c.nombre, c.capacidad, "
                    + "c.tarifa_hora, c.estado FROM cochera c "
                    + "JOIN establecimiento e ON e.id_establecimiento = c.id_establecimiento";

    private final EntityManager em;
    private final Reloj reloj;

    @Inject
    public AdminPlataformaPersistenciaAdapter(EntityManager em, Reloj reloj) {
        this.em = em;
        this.reloj = reloj;
    }

    @Override
    public List<PropietarioAdmin> propietarios(String estado, int limite) {
        String sql = "SELECT " + PROPIETARIO_COLUMNAS + " FROM propietario p "
                + "JOIN usuario u ON u.id_usuario = p.id_usuario";
        if (estado != null) {
            sql += " WHERE p.estado_validacion = :estado";
        }
        sql += " ORDER BY p.id_propietario DESC";
        var query = em.createNativeQuery(sql);
        if (estado != null) {
            query.setParameter("estado", estado);
        }
        List<?> filas = query.setMaxResults(Math.max(1, Math.min(limite, 200))).getResultList();
        List<PropietarioAdmin> propietarios = new ArrayList<>(filas.size());
        for (Object fila : filas) {
            propietarios.add(aPropietario((Object[]) fila));
        }
        return propietarios;
    }

    @Override
    public Optional<PropietarioAdmin> propietario(Long idPropietario) {
        List<?> filas = em.createNativeQuery("SELECT " + PROPIETARIO_COLUMNAS + " FROM propietario p "
                        + "JOIN usuario u ON u.id_usuario = p.id_usuario WHERE p.id_propietario = :id")
                .setParameter("id", idPropietario)
                .setMaxResults(1)
                .getResultList();
        return filas.isEmpty() ? Optional.empty() : Optional.of(aPropietario((Object[]) filas.get(0)));
    }

    @Override
    public Optional<PropietarioAdmin> propietarioDeUsuario(Long idUsuario) {
        List<?> filas = em.createNativeQuery("SELECT " + PROPIETARIO_COLUMNAS + " FROM propietario p "
                        + "JOIN usuario u ON u.id_usuario = p.id_usuario WHERE p.id_usuario = :id")
                .setParameter("id", idUsuario.intValue())
                .setMaxResults(1)
                .getResultList();
        return filas.isEmpty() ? Optional.empty() : Optional.of(aPropietario((Object[]) filas.get(0)));
    }

    @Override
    public boolean documentoPropietarioExiste(String documento) {
        List<?> filas = em.createNativeQuery(
                        "SELECT id_propietario FROM propietario WHERE documento_identidad = :documento")
                .setParameter("documento", documento)
                .setMaxResults(1)
                .getResultList();
        return !filas.isEmpty();
    }

    @Override
    @Transactional
    public Long registrarSolicitudPropietario(Long idUsuario, String documento) {
        em.createNativeQuery("INSERT INTO propietario (id_usuario, documento_identidad, "
                        + "estado_validacion) VALUES (:usuario, :documento, 'PENDIENTE')")
                .setParameter("usuario", idUsuario.intValue())
                .setParameter("documento", documento)
                .executeUpdate();
        return ((Number) em.createNativeQuery("SELECT LAST_INSERT_ID()").getSingleResult()).longValue();
    }

    @Override
    @Transactional
    public boolean cambiarValidacion(Long idPropietario, String esperado, String nuevo) {
        return em.createNativeQuery("UPDATE propietario SET estado_validacion = :nuevo "
                        + "WHERE id_propietario = :id AND estado_validacion = :esperado")
                .setParameter("nuevo", nuevo)
                .setParameter("id", idPropietario)
                .setParameter("esperado", esperado)
                .executeUpdate() > 0;
    }

    @Override
    public List<SupervisionAdmin.Establecimiento> establecimientos(String estado, int limite) {
        String sql = "SELECT e.id_establecimiento, e.nombre, d.nombre, "
                + "CONCAT(u.nombres, ' ', u.apellidos), e.estado, COUNT(c.id_cancha) "
                + "FROM establecimiento e "
                + "JOIN propietario p ON p.id_propietario = e.id_propietario "
                + "JOIN usuario u ON u.id_usuario = p.id_usuario "
                + "JOIN distrito d ON d.id_distrito = e.id_distrito "
                + "LEFT JOIN cancha c ON c.id_establecimiento = e.id_establecimiento";
        if (estado != null) {
            sql += " WHERE e.estado = :estado";
        }
        sql += " GROUP BY e.id_establecimiento, e.nombre, d.nombre, u.nombres, u.apellidos, e.estado "
                + "ORDER BY e.id_establecimiento DESC";
        var query = em.createNativeQuery(sql);
        if (estado != null) {
            query.setParameter("estado", estado);
        }
        List<?> filas = query.setMaxResults(Math.max(1, Math.min(limite, 200))).getResultList();
        List<SupervisionAdmin.Establecimiento> lista = new ArrayList<>(filas.size());
        for (Object fila : filas) {
            Object[] f = (Object[]) fila;
            lista.add(new SupervisionAdmin.Establecimiento(
                    ((Number) f[0]).longValue(), (String) f[1], (String) f[2], (String) f[3],
                    (String) f[4], ((Number) f[5]).longValue()));
        }
        return lista;
    }

    @Override
    public Optional<SupervisionAdmin.Establecimiento> establecimiento(Long idEstablecimiento) {
        List<?> filas = em.createNativeQuery("SELECT e.id_establecimiento, e.nombre, d.nombre, "
                        + "CONCAT(u.nombres, ' ', u.apellidos), e.estado, COUNT(c.id_cancha) "
                        + "FROM establecimiento e "
                        + "JOIN propietario p ON p.id_propietario = e.id_propietario "
                        + "JOIN usuario u ON u.id_usuario = p.id_usuario "
                        + "JOIN distrito d ON d.id_distrito = e.id_distrito "
                        + "LEFT JOIN cancha c ON c.id_establecimiento = e.id_establecimiento "
                        + "WHERE e.id_establecimiento = :id "
                        + "GROUP BY e.id_establecimiento, e.nombre, d.nombre, u.nombres, u.apellidos, e.estado")
                .setParameter("id", idEstablecimiento)
                .setMaxResults(1)
                .getResultList();
        if (filas.isEmpty()) {
            return Optional.empty();
        }
        Object[] f = (Object[]) filas.get(0);
        return Optional.of(new SupervisionAdmin.Establecimiento(
                ((Number) f[0]).longValue(), (String) f[1], (String) f[2], (String) f[3],
                (String) f[4], ((Number) f[5]).longValue()));
    }

    @Override
    @Transactional
    public boolean cambiarEstadoEstablecimiento(Long idEstablecimiento, String esperado, String nuevo) {
        return em.createNativeQuery("UPDATE establecimiento SET estado = :nuevo "
                        + "WHERE id_establecimiento = :id AND estado = :esperado")
                .setParameter("nuevo", nuevo)
                .setParameter("id", idEstablecimiento)
                .setParameter("esperado", esperado)
                .executeUpdate() > 0;
    }

    @Override
    public List<SupervisionAdmin.Reserva> reservas(String estado, int limite) {
        String sql = "SELECT r.id_reserva, c.nombre, e.nombre, CONCAT(u.nombres, ' ', u.apellidos), "
                + "CAST(r.inicio AS CHAR), CAST(r.fin AS CHAR), r.monto_cancha, "
                + "COALESCE(rc.monto, 0), r.estado "
                + "FROM reserva r "
                + "JOIN cancha c ON c.id_cancha = r.id_cancha "
                + "JOIN establecimiento e ON e.id_establecimiento = c.id_establecimiento "
                + "JOIN usuario u ON u.id_usuario = r.id_usuario "
                + "LEFT JOIN reserva_cochera rc ON rc.id_reserva = r.id_reserva";
        if (estado != null) {
            sql += " WHERE r.estado = :estado";
        }
        sql += " ORDER BY r.id_reserva DESC";
        var query = em.createNativeQuery(sql);
        if (estado != null) {
            query.setParameter("estado", estado);
        }
        List<?> filas = query.setMaxResults(Math.max(1, Math.min(limite, 200))).getResultList();
        List<SupervisionAdmin.Reserva> lista = new ArrayList<>(filas.size());
        for (Object fila : filas) {
            Object[] f = (Object[]) fila;
            lista.add(new SupervisionAdmin.Reserva(
                    ((Number) f[0]).longValue(), (String) f[1], (String) f[2], (String) f[3],
                    SqlUtc.aInstant(f[4]), SqlUtc.aInstant(f[5]),
                    (BigDecimal) f[6], (BigDecimal) f[7], (String) f[8]));
        }
        return lista;
    }

    @Override
    public List<SupervisionAdmin.Pago> pagos(String estado, int limite) {
        String sql = "SELECT p.id_pago, p.id_reserva, p.monto, p.medio_pago, p.estado, "
                + "CAST(p.fecha_pago AS CHAR), "
                + "COALESCE((SELECT SUM(r.monto) FROM reembolso r WHERE r.id_pago = p.id_pago), 0) "
                + "FROM pago p";
        if (estado != null) {
            sql += " WHERE p.estado = :estado";
        }
        sql += " ORDER BY p.id_pago DESC";
        var query = em.createNativeQuery(sql);
        if (estado != null) {
            query.setParameter("estado", estado);
        }
        List<?> filas = query.setMaxResults(Math.max(1, Math.min(limite, 200))).getResultList();
        List<SupervisionAdmin.Pago> lista = new ArrayList<>(filas.size());
        for (Object fila : filas) {
            Object[] f = (Object[]) fila;
            lista.add(new SupervisionAdmin.Pago(
                    ((Number) f[0]).longValue(), ((Number) f[1]).longValue(), (BigDecimal) f[2],
                    (String) f[3], (String) f[4], SqlUtc.aInstant(f[5]), (BigDecimal) f[6]));
        }
        return lista;
    }

    @Override
    public List<Auditoria> auditoria(int limite) {
        List<?> filas = em.createNativeQuery("SELECT id_auditoria, id_usuario, accion, entidad, "
                        + "id_entidad, detalle, CAST(fecha_evento AS CHAR) FROM auditoria "
                        + "ORDER BY id_auditoria DESC")
                .setMaxResults(Math.max(1, Math.min(limite, 500)))
                .getResultList();
        List<Auditoria> lista = new ArrayList<>(filas.size());
        for (Object fila : filas) {
            Object[] f = (Object[]) fila;
            lista.add(new Auditoria(
                    ((Number) f[0]).longValue(),
                    f[1] == null ? null : ((Number) f[1]).longValue(),
                    (String) f[2], (String) f[3], (String) f[4], (String) f[5],
                    SqlUtc.aInstant(f[6])));
        }
        return lista;
    }

    @Override
    @Transactional
    public void registrar(Long idUsuario, String accion, String entidad, String idEntidad, String detalle) {
        em.createNativeQuery("INSERT INTO auditoria (id_usuario, accion, entidad, id_entidad, detalle, fecha_evento) "
                        + "VALUES (:idUsuario, :accion, :entidad, :idEntidad, :detalle, :fecha)")
                .setParameter("idUsuario", idUsuario)
                .setParameter("accion", recortar(accion, 120))
                .setParameter("entidad", recortar(entidad, 80))
                .setParameter("idEntidad", recortar(idEntidad, 60))
                .setParameter("detalle", recortar(detalle, 800))
                .setParameter("fecha", LocalDateTime.ofInstant(reloj.ahora(), ZoneOffset.UTC))
                .executeUpdate();
    }

    @Override
    public List<UsuarioAdmin> usuarios(String correo, String estado, String rol, int limite) {
        StringBuilder sql = new StringBuilder(USUARIO_COLUMNAS + " WHERE 1 = 1");
        if (correo != null) {
            sql.append(" AND u.correo LIKE :correo");
        }
        if (estado != null) {
            sql.append(" AND u.estado = :estado");
        }
        if (rol != null) {
            sql.append(" AND r.nombre = :rol");
        }
        sql.append(" GROUP BY u.id_usuario, u.nombres, u.apellidos, u.correo, u.telefono, u.estado, ")
                .append("u.fecha_registro ORDER BY u.id_usuario DESC");
        var query = em.createNativeQuery(sql.toString());
        if (correo != null) {
            query.setParameter("correo", "%" + correo + "%");
        }
        if (estado != null) {
            query.setParameter("estado", estado);
        }
        if (rol != null) {
            query.setParameter("rol", rol);
        }
        List<?> filas = query.setMaxResults(Math.max(1, Math.min(limite, 200))).getResultList();
        List<UsuarioAdmin> usuarios = new ArrayList<>(filas.size());
        for (Object fila : filas) {
            usuarios.add(aUsuario((Object[]) fila));
        }
        return usuarios;
    }

    @Override
    public Optional<UsuarioAdmin> usuario(Long idUsuario) {
        List<?> filas = em.createNativeQuery(USUARIO_COLUMNAS + " WHERE u.id_usuario = :id "
                        + "GROUP BY u.id_usuario, u.nombres, u.apellidos, u.correo, u.telefono, u.estado, "
                        + "u.fecha_registro")
                .setParameter("id", idUsuario)
                .setMaxResults(1)
                .getResultList();
        return filas.isEmpty() ? Optional.empty() : Optional.of(aUsuario((Object[]) filas.get(0)));
    }

    @Override
    @Transactional
    public void reemplazarRoles(Long idUsuario, Set<String> roles) {
        em.createNativeQuery("DELETE FROM usuario_rol WHERE id_usuario = :id")
                .setParameter("id", idUsuario)
                .executeUpdate();
        for (String rol : roles) {
            em.createNativeQuery("INSERT INTO usuario_rol (id_usuario, id_rol) "
                            + "SELECT :id, id_rol FROM rol WHERE nombre = :rol")
                    .setParameter("id", idUsuario)
                    .setParameter("rol", rol)
                    .executeUpdate();
        }
    }

    @Override
    @Transactional
    public void invalidarTokensDeUsuario(Long idUsuario) {
        em.createNativeQuery("UPDATE usuario SET token_version = token_version + 1 "
                        + "WHERE id_usuario = :id")
                .setParameter("id", idUsuario.intValue())
                .executeUpdate();
    }

    @Override
    @Transactional
    public boolean cambiarEstado(Long idUsuario, String esperado, String nuevo) {
        return em.createNativeQuery("UPDATE usuario SET estado = :nuevo "
                        + "WHERE id_usuario = :id AND estado = :esperado")
                .setParameter("nuevo", nuevo)
                .setParameter("id", idUsuario)
                .setParameter("esperado", esperado)
                .executeUpdate() > 0;
    }

    @Override
    public long contarOtrosAdminsActivos(Long idUsuarioExcluido) {
        return ((Number) em.createNativeQuery("SELECT COUNT(DISTINCT u.id_usuario) FROM usuario u "
                        + "JOIN usuario_rol ur ON ur.id_usuario = u.id_usuario "
                        + "JOIN rol r ON r.id_rol = ur.id_rol "
                        + "WHERE r.nombre = 'PLATFORM_ADMIN' AND u.estado = 'ACTIVO' "
                        + "AND u.id_usuario <> :id")
                .setParameter("id", idUsuarioExcluido)
                .getSingleResult()).longValue();
    }

    @Override
    public List<PromocionAdmin> promociones(String estado, int limite) {
        StringBuilder sql = new StringBuilder(PROMOCION_COLUMNAS);
        if (estado != null) {
            sql.append(" WHERE p.estado = :estado");
        }
        sql.append(" GROUP BY p.id_promocion, p.codigo, p.descripcion, p.tipo_descuento, p.valor, ")
                .append("p.fecha_inicio, p.fecha_fin, p.estado, p.limite_usos, p.limite_por_usuario ")
                .append("ORDER BY p.id_promocion DESC");
        var query = em.createNativeQuery(sql.toString());
        if (estado != null) {
            query.setParameter("estado", estado);
        }
        List<?> filas = query.setMaxResults(Math.max(1, Math.min(limite, 200))).getResultList();
        List<PromocionAdmin> promociones = new ArrayList<>(filas.size());
        for (Object fila : filas) {
            promociones.add(aPromocion((Object[]) fila));
        }
        return promociones;
    }

    @Override
    public Optional<PromocionAdmin> promocion(Long idPromocion) {
        List<?> filas = em.createNativeQuery(PROMOCION_COLUMNAS
                        + " WHERE p.id_promocion = :id GROUP BY p.id_promocion, p.codigo, p.descripcion, "
                        + "p.tipo_descuento, p.valor, p.fecha_inicio, p.fecha_fin, p.estado, "
                        + "p.limite_usos, p.limite_por_usuario")
                .setParameter("id", idPromocion)
                .setMaxResults(1)
                .getResultList();
        return filas.isEmpty() ? Optional.empty() : Optional.of(aPromocion((Object[]) filas.get(0)));
    }

    @Override
    public boolean codigoPromocionExiste(String codigo) {
        return !em.createNativeQuery("SELECT 1 FROM promocion WHERE codigo = :codigo")
                .setParameter("codigo", codigo)
                .setMaxResults(1)
                .getResultList().isEmpty();
    }

    @Override
    public long contarCanchas(List<Long> idsCancha) {
        if (idsCancha == null || idsCancha.isEmpty()) {
            return 0;
        }
        return ((Number) em.createNativeQuery("SELECT COUNT(*) FROM cancha WHERE id_cancha IN (:ids)")
                .setParameter("ids", idsCancha)
                .getSingleResult()).longValue();
    }

    @Override
    @Transactional
    public PromocionAdmin registrarPromocion(PromocionAdmin promocion) {
        em.createNativeQuery("INSERT INTO promocion (codigo, descripcion, tipo_descuento, valor, "
                        + "fecha_inicio, fecha_fin, estado, limite_usos, limite_por_usuario) "
                        + "VALUES (:codigo, :descripcion, :tipo, :valor, :inicio, :fin, :estado, "
                        + ":limiteUsos, :limitePorUsuario)")
                .setParameter("codigo", promocion.codigo())
                .setParameter("descripcion", promocion.descripcion())
                .setParameter("tipo", promocion.tipoDescuento())
                .setParameter("valor", promocion.valor())
                .setParameter("inicio", java.sql.Date.valueOf(promocion.fechaInicio()))
                .setParameter("fin", java.sql.Date.valueOf(promocion.fechaFin()))
                .setParameter("estado", promocion.estado())
                .setParameter("limiteUsos", promocion.limiteUsos())
                .setParameter("limitePorUsuario", promocion.limitePorUsuario())
                .executeUpdate();
        Long id = ((Number) em.createNativeQuery("SELECT LAST_INSERT_ID()").getSingleResult()).longValue();
        for (Long idCancha : promocion.canchas()) {
            em.createNativeQuery("INSERT INTO cancha_promocion (id_cancha, id_promocion) "
                            + "VALUES (:idCancha, :idPromocion)")
                    .setParameter("idCancha", idCancha)
                    .setParameter("idPromocion", id)
                    .executeUpdate();
        }
        return new PromocionAdmin(id, promocion.codigo(), promocion.descripcion(),
                promocion.tipoDescuento(), promocion.valor(), promocion.fechaInicio(),
                promocion.fechaFin(), promocion.estado(), promocion.limiteUsos(),
                promocion.limitePorUsuario(), 0, promocion.canchas());
    }

    @Override
    @Transactional
    public boolean cambiarEstadoPromocion(Long idPromocion, String esperado, String nuevo) {
        return em.createNativeQuery("UPDATE promocion SET estado = :nuevo "
                        + "WHERE id_promocion = :id AND estado = :esperado")
                .setParameter("nuevo", nuevo)
                .setParameter("id", idPromocion)
                .setParameter("esperado", esperado)
                .executeUpdate() > 0;
    }

    @Override
    public List<CocheraAdmin> cocheras(String estado, int limite) {
        StringBuilder sql = new StringBuilder(COCHERA_COLUMNAS);
        if (estado != null) {
            sql.append(" WHERE c.estado = :estado");
        }
        sql.append(" ORDER BY c.id_cochera DESC");
        var query = em.createNativeQuery(sql.toString());
        if (estado != null) {
            query.setParameter("estado", estado);
        }
        List<?> filas = query.setMaxResults(Math.max(1, Math.min(limite, 200))).getResultList();
        List<CocheraAdmin> cocheras = new ArrayList<>(filas.size());
        for (Object fila : filas) {
            cocheras.add(aCochera((Object[]) fila));
        }
        return cocheras;
    }

    @Override
    public Optional<CocheraAdmin> cochera(Long idCochera) {
        List<?> filas = em.createNativeQuery(COCHERA_COLUMNAS + " WHERE c.id_cochera = :id")
                .setParameter("id", idCochera)
                .setMaxResults(1)
                .getResultList();
        return filas.isEmpty() ? Optional.empty() : Optional.of(aCochera((Object[]) filas.get(0)));
    }

    @Override
    @Transactional
    public boolean cambiarEstadoCochera(Long idCochera, String esperado, String nuevo) {
        return em.createNativeQuery("UPDATE cochera SET estado = :nuevo "
                        + "WHERE id_cochera = :id AND estado = :esperado")
                .setParameter("nuevo", nuevo)
                .setParameter("id", idCochera)
                .setParameter("esperado", esperado)
                .executeUpdate() > 0;
    }

    @Override
    public Optional<Promocion> vigente(String codigo, Long idCancha, LocalDate fecha, Long idUsuario) {
        List<?> filas = em.createNativeQuery("SELECT p.id_promocion, p.codigo, p.tipo_descuento, p.valor "
                        + "FROM promocion p "
                        + "JOIN cancha_promocion cp ON cp.id_promocion = p.id_promocion "
                        + "WHERE p.codigo = :codigo AND cp.id_cancha = :idCancha "
                        + "AND p.estado = 'ACTIVA' AND :fecha BETWEEN p.fecha_inicio AND p.fecha_fin "
                        + "AND (p.limite_usos IS NULL OR (SELECT COUNT(*) FROM reserva r "
                        + "WHERE r.id_promocion = p.id_promocion "
                        + "AND r.estado NOT IN ('CANCELADA','RECHAZADA','EXPIRADA')) < p.limite_usos) "
                        + "AND (p.limite_por_usuario IS NULL OR (SELECT COUNT(*) FROM reserva r "
                        + "WHERE r.id_promocion = p.id_promocion AND r.id_usuario = :idUsuario "
                        + "AND r.estado NOT IN ('CANCELADA','RECHAZADA','EXPIRADA')) < p.limite_por_usuario)")
                .setParameter("codigo", codigo)
                .setParameter("idCancha", idCancha)
                .setParameter("fecha", java.sql.Date.valueOf(fecha))
                .setParameter("idUsuario", idUsuario.intValue())
                .setMaxResults(1)
                .getResultList();
        if (filas.isEmpty()) {
            return Optional.empty();
        }
        Object[] f = (Object[]) filas.get(0);
        return Optional.of(new Promocion(((Number) f[0]).longValue(), (String) f[1], (String) f[2],
                (BigDecimal) f[3]));
    }

    @Override
    @Transactional
    public boolean consumir(Long idPromocion, Long idUsuario) {
        List<?> limites = em.createNativeQuery("SELECT limite_usos, limite_por_usuario FROM promocion "
                        + "WHERE id_promocion = :id FOR UPDATE")
                .setParameter("id", idPromocion)
                .setMaxResults(1)
                .getResultList();
        if (limites.isEmpty()) {
            return false;
        }
        Object[] fila = (Object[]) limites.get(0);
        Integer limiteUsos = fila[0] == null ? null : ((Number) fila[0]).intValue();
        Integer limitePorUsuario = fila[1] == null ? null : ((Number) fila[1]).intValue();
        if (limiteUsos == null && limitePorUsuario == null) {
            return true;
        }
        List<?> usos = em.createNativeQuery("SELECT id_usuario FROM reserva "
                        + "WHERE id_promocion = :id "
                        + "AND estado NOT IN ('CANCELADA','RECHAZADA','EXPIRADA') FOR UPDATE")
                .setParameter("id", idPromocion)
                .getResultList();
        long total = usos.size();
        long delUsuario = usos.stream()
                .filter(filaUso -> ((Number) filaUso).longValue() == idUsuario.longValue())
                .count();
        return (limiteUsos == null || total <= limiteUsos)
                && (limitePorUsuario == null || delUsuario <= limitePorUsuario);
    }

    @Override
    public boolean esPropietarioDeEstablecimiento(Long idEstablecimiento, Long idUsuario) {
        return !em.createNativeQuery("SELECT 1 FROM establecimiento e "
                        + "JOIN propietario p ON p.id_propietario = e.id_propietario "
                        + "WHERE e.id_establecimiento = :id AND p.id_usuario = :idUsuario")
                .setParameter("id", idEstablecimiento)
                .setParameter("idUsuario", idUsuario)
                .setMaxResults(1)
                .getResultList().isEmpty();
    }

    @Override
    public List<Long> canchasDeEstablecimiento(Long idEstablecimiento) {
        List<?> filas = em.createNativeQuery("SELECT id_cancha FROM cancha "
                        + "WHERE id_establecimiento = :id ORDER BY id_cancha")
                .setParameter("id", idEstablecimiento)
                .getResultList();
        List<Long> canchas = new ArrayList<>(filas.size());
        for (Object fila : filas) {
            canchas.add(((Number) fila).longValue());
        }
        return canchas;
    }

    @Override
    public List<PromocionAdmin> promocionesDeEstablecimiento(Long idEstablecimiento, int limite) {
        List<?> filas = em.createNativeQuery(PROMOCION_COLUMNAS
                        + " WHERE EXISTS (SELECT 1 FROM cancha_promocion cp2 "
                        + "JOIN cancha c2 ON c2.id_cancha = cp2.id_cancha "
                        + "WHERE cp2.id_promocion = p.id_promocion AND c2.id_establecimiento = :id) "
                        + "GROUP BY p.id_promocion, p.codigo, p.descripcion, p.tipo_descuento, p.valor, "
                        + "p.fecha_inicio, p.fecha_fin, p.estado, p.limite_usos, p.limite_por_usuario "
                        + "ORDER BY p.id_promocion DESC")
                .setParameter("id", idEstablecimiento)
                .setMaxResults(Math.max(1, Math.min(limite, 200)))
                .getResultList();
        List<PromocionAdmin> promociones = new ArrayList<>(filas.size());
        for (Object fila : filas) {
            promociones.add(aPromocion((Object[]) fila));
        }
        return promociones;
    }

    @Override
    public boolean promocionDeEstablecimiento(Long idPromocion, Long idEstablecimiento) {
        return !em.createNativeQuery("SELECT 1 FROM cancha_promocion cp "
                        + "JOIN cancha c ON c.id_cancha = cp.id_cancha "
                        + "WHERE cp.id_promocion = :idPromocion AND c.id_establecimiento = :id")
                .setParameter("idPromocion", idPromocion)
                .setParameter("id", idEstablecimiento)
                .setMaxResults(1)
                .getResultList().isEmpty();
    }

    @Override
    public ReporteAdmin reporte(int dias, Instant desde, Instant hasta) {
        Object[] f = (Object[]) em.createNativeQuery("SELECT "
                        + "(SELECT COUNT(*) FROM usuario), "
                        + "(SELECT COUNT(*) FROM usuario WHERE fecha_registro >= :desde "
                        + "AND fecha_registro < :hasta), "
                        + "(SELECT COUNT(*) FROM propietario WHERE estado_validacion = 'VALIDADO'), "
                        + "(SELECT COUNT(*) FROM establecimiento WHERE estado = 'ACTIVO'), "
                        + "(SELECT COUNT(*) FROM reserva WHERE fecha_creacion >= :desde "
                        + "AND fecha_creacion < :hasta), "
                        + "(SELECT COALESCE(SUM(monto), 0) FROM pago WHERE estado = 'APROBADO' "
                        + "AND fecha_pago >= :desde AND fecha_pago < :hasta), "
                        + "(SELECT COUNT(*) FROM pago WHERE estado = 'APROBADO' "
                        + "AND fecha_pago >= :desde AND fecha_pago < :hasta), "
                        + "(SELECT COUNT(*) FROM reclamo WHERE estado IN ('ABIERTO','EN_REVISION'))")
                .setParameter("desde", SqlUtc.aLiteral(desde))
                .setParameter("hasta", SqlUtc.aLiteral(hasta))
                .getSingleResult();

        ReporteAdmin.Resumen resumen = new ReporteAdmin.Resumen(
                ((Number) f[0]).longValue(), ((Number) f[1]).longValue(),
                ((Number) f[2]).longValue(), ((Number) f[3]).longValue(),
                ((Number) f[4]).longValue(),
                new BigDecimal(f[5].toString()).setScale(2, RoundingMode.HALF_UP),
                ((Number) f[6]).longValue(), ((Number) f[7]).longValue());

        Map<LocalDate, Long> reservas = mapaConteo(
                "SELECT DATE(fecha_creacion), COUNT(*) FROM reserva "
                        + "WHERE fecha_creacion >= :desde AND fecha_creacion < :hasta "
                        + "GROUP BY DATE(fecha_creacion)", desde, hasta);
        Map<LocalDate, Long> usuarios = mapaConteo(
                "SELECT DATE(fecha_registro), COUNT(*) FROM usuario "
                        + "WHERE fecha_registro >= :desde AND fecha_registro < :hasta "
                        + "GROUP BY DATE(fecha_registro)", desde, hasta);
        Map<LocalDate, BigDecimal> ingresos = new HashMap<>();
        List<?> ingresosFilas = em.createNativeQuery("SELECT DATE(fecha_pago), COALESCE(SUM(monto), 0) "
                        + "FROM pago WHERE estado = 'APROBADO' "
                        + "AND fecha_pago >= :desde AND fecha_pago < :hasta "
                        + "GROUP BY DATE(fecha_pago)")
                .setParameter("desde", SqlUtc.aLiteral(desde))
                .setParameter("hasta", SqlUtc.aLiteral(hasta))
                .getResultList();
        for (Object fila : ingresosFilas) {
            Object[] i = (Object[]) fila;
            ingresos.put(LocalDate.parse(i[0].toString()),
                    new BigDecimal(i[1].toString()).setScale(2, RoundingMode.HALF_UP));
        }
        LocalDate inicio = LocalDate.ofInstant(desde, ZONA_NEGOCIO);
        List<ReporteAdmin.PuntoDia> serie = new ArrayList<>(dias);
        for (int i = 0; i < dias; i++) {
            LocalDate fecha = inicio.plusDays(i);
            serie.add(new ReporteAdmin.PuntoDia(fecha, reservas.getOrDefault(fecha, 0L),
                    usuarios.getOrDefault(fecha, 0L),
                    ingresos.getOrDefault(fecha, BigDecimal.ZERO.setScale(2))));
        }

        List<?> estadosFilas = em.createNativeQuery("SELECT estado, COUNT(*) FROM reserva "
                        + "WHERE fecha_creacion >= :desde AND fecha_creacion < :hasta "
                        + "GROUP BY estado ORDER BY COUNT(*) DESC")
                .setParameter("desde", SqlUtc.aLiteral(desde))
                .setParameter("hasta", SqlUtc.aLiteral(hasta))
                .getResultList();
        List<ReporteAdmin.EstadoReserva> estados = new ArrayList<>(estadosFilas.size());
        for (Object fila : estadosFilas) {
            Object[] e = (Object[]) fila;
            estados.add(new ReporteAdmin.EstadoReserva((String) e[0], ((Number) e[1]).longValue()));
        }

        List<?> topFilas = em.createNativeQuery("SELECT e.id_establecimiento, e.nombre, d.nombre, "
                        + "COUNT(r.id_reserva), COALESCE(SUM(CASE WHEN r.estado IN ('CONFIRMADA','FINALIZADA') "
                        + "THEN (r.monto_cancha - r.monto_descuento) ELSE 0 END), 0) "
                        + "FROM establecimiento e JOIN distrito d ON d.id_distrito = e.id_distrito "
                        + "LEFT JOIN cancha c ON c.id_establecimiento = e.id_establecimiento "
                        + "LEFT JOIN reserva r ON r.id_cancha = c.id_cancha "
                        + "AND r.fecha_creacion >= :desde AND r.fecha_creacion < :hasta "
                        + "GROUP BY e.id_establecimiento, e.nombre, d.nombre "
                        + "HAVING COUNT(r.id_reserva) > 0 ORDER BY COUNT(r.id_reserva) DESC LIMIT 5")
                .setParameter("desde", SqlUtc.aLiteral(desde))
                .setParameter("hasta", SqlUtc.aLiteral(hasta))
                .getResultList();
        List<ReporteAdmin.EstablecimientoTop> establecimientos = new ArrayList<>(topFilas.size());
        for (Object fila : topFilas) {
            Object[] e = (Object[]) fila;
            establecimientos.add(new ReporteAdmin.EstablecimientoTop(
                    ((Number) e[0]).longValue(), (String) e[1], (String) e[2],
                    ((Number) e[3]).longValue(),
                    new BigDecimal(e[4].toString()).setScale(2, RoundingMode.HALF_UP)));
        }

        return new ReporteAdmin(dias, desde, hasta, resumen, serie, estados, establecimientos);
    }

    private Map<LocalDate, Long> mapaConteo(String sql, Instant desde, Instant hasta) {
        Map<LocalDate, Long> conteo = new HashMap<>();
        List<?> filas = em.createNativeQuery(sql)
                .setParameter("desde", SqlUtc.aLiteral(desde))
                .setParameter("hasta", SqlUtc.aLiteral(hasta))
                .getResultList();
        for (Object fila : filas) {
            Object[] f = (Object[]) fila;
            conteo.put(LocalDate.parse(f[0].toString()), ((Number) f[1]).longValue());
        }
        return conteo;
    }

    private static PromocionAdmin aPromocion(Object[] f) {
        List<Long> canchas = new ArrayList<>();
        if (f[11] != null) {
            for (String id : ((String) f[11]).split(",")) {
                if (!id.isBlank()) {
                    canchas.add(Long.valueOf(id.trim()));
                }
            }
        }
        return new PromocionAdmin(
                ((Number) f[0]).longValue(),
                (String) f[1],
                (String) f[2],
                (String) f[3],
                (BigDecimal) f[4],
                java.time.LocalDate.parse((String) f[5]),
                java.time.LocalDate.parse((String) f[6]),
                (String) f[7],
                f[8] == null ? null : ((Number) f[8]).intValue(),
                f[9] == null ? null : ((Number) f[9]).intValue(),
                ((Number) f[10]).longValue(),
                canchas);
    }

    private static CocheraAdmin aCochera(Object[] f) {
        return new CocheraAdmin(
                ((Number) f[0]).longValue(),
                ((Number) f[1]).longValue(),
                (String) f[2],
                (String) f[3],
                ((Number) f[4]).intValue(),
                (BigDecimal) f[5],
                (String) f[6]);
    }

    private static UsuarioAdmin aUsuario(Object[] f) {
        List<String> roles = new ArrayList<>();
        if (f[6] != null) {
            Arrays.stream(((String) f[6]).split(","))
                    .map(String::trim)
                    .filter(rol -> !rol.isEmpty())
                    .forEach(roles::add);
        }
        return new UsuarioAdmin(
                ((Number) f[0]).longValue(),
                (String) f[1],
                (String) f[2],
                (String) f[3],
                (String) f[4],
                roles,
                SqlUtc.aInstant(f[5]));
    }

    private static PropietarioAdmin aPropietario(Object[] f) {
        return new PropietarioAdmin(
                ((Number) f[0]).longValue(),
                ((Number) f[1]).longValue(),
                (String) f[2],
                (String) f[3],
                (String) f[4],
                PropietarioAdmin.Estado.valueOf((String) f[5]));
    }

    private static String recortar(String texto, int maximo) {
        if (texto == null) {
            return null;
        }
        return texto.length() <= maximo ? texto : texto.substring(0, maximo);
    }
}
