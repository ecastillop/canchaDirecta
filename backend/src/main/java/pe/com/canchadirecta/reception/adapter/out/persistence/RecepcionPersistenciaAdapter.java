package pe.com.canchadirecta.reception.adapter.out.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import pe.com.canchadirecta.reception.application.dto.ClienteRecepcion;
import pe.com.canchadirecta.reception.application.dto.CanchaRecepcion;
import pe.com.canchadirecta.reception.application.dto.EstablecimientoRecepcion;
import pe.com.canchadirecta.reception.application.dto.IncidenciaRecepcion;
import pe.com.canchadirecta.reception.application.dto.OperadorAsignado;
import pe.com.canchadirecta.reception.application.dto.ReservaRecepcion;
import pe.com.canchadirecta.reception.application.port.out.RecepcionRepositorio;
import pe.com.canchadirecta.shared.adapter.out.persistence.SqlUtc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * SQL nativo de recepcion (ADR-0012).
 *
 * <p>El predicado de acceso se resuelve en SQL segun el rol: `PLATFORM_ADMIN` ve todas las
 * sedes, `OPERADOR` solo las asignadas y activas, y `OWNER` las propias (ADR-0038).</p>
 */
@ApplicationScoped
public class RecepcionPersistenciaAdapter implements RecepcionRepositorio {

    /** Predicado de acceso del usuario `:idUsuario` a la sede `e`. */
    private static final String ACCESO = """
            (EXISTS (SELECT 1 FROM usuario_rol ur JOIN rol r ON r.id_rol = ur.id_rol
                     WHERE ur.id_usuario = :idUsuario AND r.nombre = 'PLATFORM_ADMIN')
             OR EXISTS (SELECT 1 FROM operador_establecimiento oe
                        WHERE oe.id_usuario = :idUsuario
                          AND oe.id_establecimiento = e.id_establecimiento AND oe.activo = 1)
             OR e.id_propietario = (SELECT p.id_propietario FROM propietario p
                                    WHERE p.id_usuario = :idUsuario))
            """;

    private static final String SELECT_RESERVA = """
            SELECT r.id_reserva, e.id_establecimiento, e.nombre, c.id_cancha, c.nombre,
                   r.inicio, r.fin, r.estado,
                   (SELECT p.estado FROM pago p WHERE p.id_reserva = r.id_reserva
                    ORDER BY p.id_pago DESC LIMIT 1),
                   r.monto_cancha - r.monto_descuento
                     + COALESCE((SELECT rc.monto FROM reserva_cochera rc WHERE rc.id_reserva = r.id_reserva), 0),
                   u.id_usuario, CONCAT(u.nombres, ' ', u.apellidos), u.telefono
            FROM reserva r
            JOIN cancha c ON c.id_cancha = r.id_cancha
            JOIN establecimiento e ON e.id_establecimiento = c.id_establecimiento
            JOIN usuario u ON u.id_usuario = r.id_usuario
            """;

    private final EntityManager em;

    @Inject
    public RecepcionPersistenciaAdapter(EntityManager em) {
        this.em = em;
    }

    @Override
    public List<EstablecimientoRecepcion> establecimientosAccesibles(Long idUsuario) {
        List<?> filas = em.createNativeQuery("""
                        SELECT e.id_establecimiento, e.nombre, d.nombre
                        FROM establecimiento e
                        LEFT JOIN distrito d ON d.id_distrito = e.id_distrito
                        WHERE """ + ACCESO + " ORDER BY e.nombre")
                .setParameter("idUsuario", idUsuario)
                .getResultList();
        return filas.stream().map(fila -> {
            Object[] c = (Object[]) fila;
            return new EstablecimientoRecepcion(numero(c[0]), texto(c[1]), texto(c[2]));
        }).toList();
    }

    @Override
    public boolean puedeOperarEstablecimiento(Long idUsuario, Long idEstablecimiento) {
        return existe("""
                SELECT COUNT(*) FROM establecimiento e
                WHERE e.id_establecimiento = :idEstablecimiento AND """ + ACCESO,
                idUsuario, "idEstablecimiento", idEstablecimiento);
    }

    @Override
    public boolean puedeOperarCancha(Long idUsuario, Long idCancha) {
        return existe("""
                SELECT COUNT(*) FROM cancha c
                JOIN establecimiento e ON e.id_establecimiento = c.id_establecimiento
                WHERE c.id_cancha = :idCancha AND """ + ACCESO,
                idUsuario, "idCancha", idCancha);
    }

    @Override
    public boolean puedeOperarReserva(Long idUsuario, Long idReserva) {
        return existe("""
                SELECT COUNT(*) FROM reserva r
                JOIN cancha c ON c.id_cancha = r.id_cancha
                JOIN establecimiento e ON e.id_establecimiento = c.id_establecimiento
                WHERE r.id_reserva = :idReserva AND """ + ACCESO,
                idUsuario, "idReserva", idReserva);
    }

    @Override
    public List<ReservaRecepcion> reservasEntre(Long idUsuario, Long idEstablecimiento, Instant desde,
                                                Instant hasta, int limite) {
        StringBuilder sql = new StringBuilder(SELECT_RESERVA)
                .append(" WHERE ").append(ACCESO)
                .append(" AND r.inicio >= :desde AND r.inicio < :hasta");
        if (idEstablecimiento != null) {
            sql.append(" AND e.id_establecimiento = :idEstablecimientoFiltro");
        }
        sql.append(" ORDER BY r.inicio DESC LIMIT :limite");
        Query query = em.createNativeQuery(sql.toString())
                .setParameter("idUsuario", idUsuario)
                .setParameter("desde", SqlUtc.aLiteral(desde))
                .setParameter("hasta", SqlUtc.aLiteral(hasta))
                .setParameter("limite", limite);
        if (idEstablecimiento != null) {
            query.setParameter("idEstablecimientoFiltro", idEstablecimiento);
        }
        return mapear(query.getResultList());
    }

    @Override
    public List<ReservaRecepcion> listarReservas(Long idUsuario, Long idEstablecimiento, String estado, int limite) {
        StringBuilder sql = new StringBuilder(SELECT_RESERVA).append(" WHERE ").append(ACCESO);
        if (estado != null) {
            sql.append(" AND r.estado = :estado");
        }
        if (idEstablecimiento != null) {
            sql.append(" AND e.id_establecimiento = :idEstablecimientoFiltro");
        }
        sql.append(" ORDER BY r.inicio DESC LIMIT :limite");
        Query query = em.createNativeQuery(sql.toString())
                .setParameter("idUsuario", idUsuario)
                .setParameter("limite", limite);
        if (estado != null) {
            query.setParameter("estado", estado);
        }
        if (idEstablecimiento != null) {
            query.setParameter("idEstablecimientoFiltro", idEstablecimiento);
        }
        return mapear(query.getResultList());
    }

    @Override
    public Optional<ReservaRecepcion> reserva(Long idUsuario, Long idReserva) {
        List<ReservaRecepcion> filas = mapear(em.createNativeQuery(
                        SELECT_RESERVA + " WHERE " + ACCESO + " AND r.id_reserva = :idReserva")
                .setParameter("idUsuario", idUsuario)
                .setParameter("idReserva", idReserva)
                .getResultList());
        return filas.isEmpty() ? Optional.empty() : Optional.of(filas.get(0));
    }

    @Override
    public long contarReservasEntre(Long idUsuario, Long idEstablecimiento, Instant desde, Instant hasta) {
        return contarRango(idUsuario, idEstablecimiento, desde, hasta, null, false);
    }

    @Override
    public long contarEstadoEntre(Long idUsuario, Long idEstablecimiento, String estado, Instant desde,
                                  Instant hasta) {
        return contarRango(idUsuario, idEstablecimiento, desde, hasta, estado, false);
    }

    @Override
    public long contarCobrosPendientes(Long idUsuario, Long idEstablecimiento, Instant desde, Instant hasta) {
        return contarRango(idUsuario, idEstablecimiento, desde, hasta, null, true);
    }

    @Override
    public long contarCanchasActivas(Long idUsuario, Long idEstablecimiento) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(*) FROM cancha c
                JOIN establecimiento e ON e.id_establecimiento = c.id_establecimiento
                WHERE c.estado = 'ACTIVA' AND """).append(ACCESO);
        if (idEstablecimiento != null) {
            sql.append(" AND e.id_establecimiento = :idEstablecimientoFiltro");
        }
        Query query = em.createNativeQuery(sql.toString()).setParameter("idUsuario", idUsuario);
        if (idEstablecimiento != null) {
            query.setParameter("idEstablecimientoFiltro", idEstablecimiento);
        }
        return ((Number) query.getSingleResult()).longValue();
    }

    @Override
    public List<CanchaRecepcion> canchasAccesibles(Long idUsuario, Long idEstablecimiento) {
        StringBuilder sql = new StringBuilder("""
                SELECT c.id_cancha, e.id_establecimiento, c.nombre
                FROM cancha c
                JOIN establecimiento e ON e.id_establecimiento = c.id_establecimiento
                WHERE c.estado = 'ACTIVA' AND """).append(ACCESO);
        if (idEstablecimiento != null) {
            sql.append(" AND e.id_establecimiento = :idEstablecimientoFiltro");
        }
        sql.append(" ORDER BY e.nombre, c.nombre");
        Query query = em.createNativeQuery(sql.toString()).setParameter("idUsuario", idUsuario);
        if (idEstablecimiento != null) {
            query.setParameter("idEstablecimientoFiltro", idEstablecimiento);
        }
        List<?> filas = query.getResultList();
        return filas.stream().map(fila -> {
            Object[] c = (Object[]) fila;
            return new CanchaRecepcion(numero(c[0]), numero(c[1]), texto(c[2]));
        }).toList();
    }

    @Override
    public List<ClienteRecepcion> buscarClientes(String texto, int limite) {
        List<?> filas = em.createNativeQuery("""
                        SELECT u.id_usuario, CONCAT(u.nombres, ' ', u.apellidos), u.correo, u.telefono
                        FROM usuario u
                        WHERE u.estado = 'ACTIVO'
                          AND (u.correo LIKE :texto OR u.telefono LIKE :texto
                               OR CONCAT(u.nombres, ' ', u.apellidos) LIKE :texto)
                        ORDER BY u.nombres, u.apellidos
                        LIMIT :limite""")
                .setParameter("texto", "%" + texto + "%")
                .setParameter("limite", limite)
                .getResultList();
        return filas.stream().map(fila -> {
            Object[] c = (Object[]) fila;
            return new ClienteRecepcion(numero(c[0]), texto(c[1]), texto(c[2]), texto(c[3]));
        }).toList();
    }

    @Override
    public Long registrarIncidencia(Long idUsuario, Long idReserva, String asunto, String descripcion) {
        em.createNativeQuery("""
                        INSERT INTO reclamo (id_usuario, id_reserva, asunto, descripcion, estado, fecha_registro)
                        VALUES (:idUsuario, :idReserva, :asunto, :descripcion, 'ABIERTO', :fecha)""")
                .setParameter("idUsuario", idUsuario)
                .setParameter("idReserva", idReserva)
                .setParameter("asunto", asunto)
                .setParameter("descripcion", descripcion)
                .setParameter("fecha", SqlUtc.aLiteral(Instant.now()))
                .executeUpdate();
        return ((Number) em.createNativeQuery("SELECT LAST_INSERT_ID()").getSingleResult()).longValue();
    }

    @Override
    public List<IncidenciaRecepcion> incidenciasDe(Long idUsuario, int limite) {
        List<?> filas = em.createNativeQuery("""
                        SELECT r.id_reclamo, r.asunto, r.descripcion, r.estado, r.fecha_registro, r.id_reserva
                        FROM reclamo r
                        WHERE r.id_usuario = :idUsuario
                        ORDER BY r.fecha_registro DESC, r.id_reclamo DESC
                        LIMIT :limite""")
                .setParameter("idUsuario", idUsuario)
                .setParameter("limite", limite)
                .getResultList();
        return filas.stream().map(fila -> {
            Object[] c = (Object[]) fila;
            return new IncidenciaRecepcion(numero(c[0]), texto(c[1]), texto(c[2]), texto(c[3]),
                    SqlUtc.aInstant(c[4]), c[5] == null ? null : numero(c[5]));
        }).toList();
    }

    @Override
    public boolean establecimientoExiste(Long idEstablecimiento) {
        return ((Number) em.createNativeQuery(
                        "SELECT COUNT(*) FROM establecimiento WHERE id_establecimiento = :id")
                .setParameter("id", idEstablecimiento).getSingleResult()).longValue() > 0;
    }

    @Override
    public boolean usuarioExiste(Long idUsuario) {
        return ((Number) em.createNativeQuery("SELECT COUNT(*) FROM usuario WHERE id_usuario = :id")
                .setParameter("id", idUsuario).getSingleResult()).longValue() > 0;
    }

    @Override
    public List<OperadorAsignado> operadoresDe(Long idEstablecimiento) {
        List<?> filas = em.createNativeQuery("""
                        SELECT u.id_usuario, CONCAT(u.nombres, ' ', u.apellidos), u.correo, oe.cargo, oe.activo
                        FROM operador_establecimiento oe
                        JOIN usuario u ON u.id_usuario = oe.id_usuario
                        WHERE oe.id_establecimiento = :idEstablecimiento
                        ORDER BY u.nombres, u.apellidos""")
                .setParameter("idEstablecimiento", idEstablecimiento)
                .getResultList();
        return filas.stream().map(fila -> {
            Object[] c = (Object[]) fila;
            return new OperadorAsignado(numero(c[0]), texto(c[1]), texto(c[2]), texto(c[3]),
                    booleano(c[4]));
        }).toList();
    }

    @Override
    public void asignarOperador(Long idEstablecimiento, Long idUsuario, String cargo) {
        em.createNativeQuery("""
                        INSERT INTO operador_establecimiento
                          (id_usuario, id_establecimiento, cargo, activo, fecha_alta)
                        VALUES (:idUsuario, :idEstablecimiento, :cargo, 1, :fecha)
                        ON DUPLICATE KEY UPDATE cargo = VALUES(cargo), activo = 1""")
                .setParameter("idUsuario", idUsuario)
                .setParameter("idEstablecimiento", idEstablecimiento)
                .setParameter("cargo", cargo)
                .setParameter("fecha", SqlUtc.aLiteral(Instant.now()))
                .executeUpdate();
        em.createNativeQuery("""
                        INSERT IGNORE INTO usuario_rol (id_usuario, id_rol)
                        SELECT :idUsuario, id_rol FROM rol WHERE nombre = 'OPERADOR'""")
                .setParameter("idUsuario", idUsuario)
                .executeUpdate();
    }

    @Override
    public void removerOperador(Long idEstablecimiento, Long idUsuario) {
        em.createNativeQuery("""
                        DELETE FROM operador_establecimiento
                        WHERE id_usuario = :idUsuario AND id_establecimiento = :idEstablecimiento""")
                .setParameter("idUsuario", idUsuario)
                .setParameter("idEstablecimiento", idEstablecimiento)
                .executeUpdate();
        em.createNativeQuery("""
                        DELETE FROM usuario_rol
                        WHERE id_usuario = :idUsuario
                          AND id_rol = (SELECT id_rol FROM rol WHERE nombre = 'OPERADOR')
                          AND NOT EXISTS (SELECT 1 FROM operador_establecimiento
                                          WHERE id_usuario = :idUsuario)""")
                .setParameter("idUsuario", idUsuario)
                .executeUpdate();
    }

    private long contarRango(Long idUsuario, Long idEstablecimiento, Instant desde, Instant hasta,
                             String estado, boolean soloCobrosPendientes) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(*) FROM reserva r
                JOIN cancha c ON c.id_cancha = r.id_cancha
                JOIN establecimiento e ON e.id_establecimiento = c.id_establecimiento
                WHERE """).append(ACCESO)
                .append(" AND r.inicio >= :desde AND r.inicio < :hasta");
        if (estado != null) {
            sql.append(" AND r.estado = :estado");
        }
        if (soloCobrosPendientes) {
            sql.append(" AND r.estado IN ('PENDIENTE','CONFIRMADA')")
                    .append(" AND NOT EXISTS (SELECT 1 FROM pago p WHERE p.id_reserva = r.id_reserva")
                    .append(" AND p.estado = 'APROBADO')");
        }
        if (idEstablecimiento != null) {
            sql.append(" AND e.id_establecimiento = :idEstablecimientoFiltro");
        }
        Query query = em.createNativeQuery(sql.toString())
                .setParameter("idUsuario", idUsuario)
                .setParameter("desde", SqlUtc.aLiteral(desde))
                .setParameter("hasta", SqlUtc.aLiteral(hasta));
        if (estado != null) {
            query.setParameter("estado", estado);
        }
        if (idEstablecimiento != null) {
            query.setParameter("idEstablecimientoFiltro", idEstablecimiento);
        }
        return ((Number) query.getSingleResult()).longValue();
    }

    private boolean existe(String sql, Long idUsuario, String nombreParametro, Long valor) {
        Query query = em.createNativeQuery(sql).setParameter(nombreParametro, valor);
        if (idUsuario != null) {
            query.setParameter("idUsuario", idUsuario);
        }
        return ((Number) query.getSingleResult()).longValue() > 0;
    }

    private static List<ReservaRecepcion> mapear(List<?> filas) {
        return filas.stream().map(fila -> {
            Object[] c = (Object[]) fila;
            return new ReservaRecepcion(numero(c[0]), numero(c[1]), texto(c[2]), numero(c[3]), texto(c[4]),
                    SqlUtc.aInstant(c[5]), SqlUtc.aInstant(c[6]), texto(c[7]), texto(c[8]),
                    decimal(c[9]), numero(c[10]), texto(c[11]), texto(c[12]));
        }).toList();
    }

    /** MySQL devuelve `TINYINT(1)` como booleano y el resto de enteros como numero. */
    private static long numero(Object valor) {
        if (valor instanceof Boolean booleano) {
            return booleano ? 1L : 0L;
        }
        return ((Number) valor).longValue();
    }

    private static boolean booleano(Object valor) {
        if (valor instanceof Boolean booleano) {
            return booleano;
        }
        return ((Number) valor).longValue() != 0;
    }

    private static BigDecimal decimal(Object valor) {
        return valor == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(valor));
    }

    private static String texto(Object valor) {
        return valor == null ? null : String.valueOf(valor);
    }
}
