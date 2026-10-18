package pe.com.canchadirecta.bookings.adapter.out.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.transaction.Transactional;
import pe.com.canchadirecta.bookings.application.dto.CanchaInfo;
import pe.com.canchadirecta.bookings.application.dto.CocheraInfo;
import pe.com.canchadirecta.bookings.application.dto.HorarioSemanal;
import pe.com.canchadirecta.bookings.application.port.out.ReservaRepositorio;
import pe.com.canchadirecta.bookings.domain.model.FranjaHoraria;
import pe.com.canchadirecta.bookings.domain.model.Reserva;
import pe.com.canchadirecta.shared.adapter.out.persistence.SqlUtc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Persistencia de reservas con SQL nativo (ADR-0012).
 *
 * <p><b>Bloqueo (RN-01).</b> `bloquearCancha` ejecuta `SELECT ... FOR UPDATE` sobre la
 * fila de `cancha`, de modo que todas las reservas de una cancha se serializan: la
 * segunda peticion espera a que la primera confirme. Todas las operaciones se unen a la
 * transaccion abierta por el caso de uso (propagacion REQUIRED), por lo que el bloqueo
 * sigue vigente hasta ese commit.</p>
 *
 * <p><b>Fechas (L-04).</b> Las columnas del esquema son `DATETIME` sin zona y en este
 * modulo se tratan explicitamente como UTC a traves de {@link SqlUtc} (ADR-0014), de modo
 * que ni Hibernate ni el driver reinterpretan la hora con la zona de la JVM.</p>
 */
@ApplicationScoped
public class ReservaPersistenciaAdapter implements ReservaRepositorio {

    /** Columnas de `reserva` en el orden que espera {@link #aReserva(Object[])}. */
    private static final String COLUMNAS_RESERVA =
            "r.id_reserva, r.id_usuario, r.id_cancha, CAST(r.inicio AS CHAR), CAST(r.fin AS CHAR), "
                    + "r.monto_cancha, r.monto_descuento, r.estado, CAST(r.fecha_creacion AS CHAR), "
                    + "rc.id_cochera, rc.monto";

    /** `reserva` con su cochera opcional (RN-08) en una unica consulta. */
    private static final String RESERVA_CON_COCHERA =
            " FROM reserva r LEFT JOIN reserva_cochera rc ON rc.id_reserva = r.id_reserva";

    private final EntityManager em;

    @Inject
    public ReservaPersistenciaAdapter(EntityManager em) {
        this.em = em;
    }

    @Override
    @Transactional
    public Optional<CanchaInfo> bloquearCancha(Long idCancha) {
        List<?> filas = em.createNativeQuery(
                        "SELECT id_cancha, id_establecimiento, precio_base, estado FROM cancha "
                                + "WHERE id_cancha = :id FOR UPDATE")
                .setParameter("id", idCancha.intValue())
                .setMaxResults(1)
                .getResultList();
        return filas.isEmpty() ? Optional.empty() : Optional.of(aCanchaInfo((Object[]) filas.get(0)));
    }

    @Override
    public Optional<CanchaInfo> buscarCancha(Long idCancha) {
        List<?> filas = em.createNativeQuery(
                        "SELECT id_cancha, id_establecimiento, precio_base, estado FROM cancha "
                                + "WHERE id_cancha = :id")
                .setParameter("id", idCancha.intValue())
                .setMaxResults(1)
                .getResultList();
        return filas.isEmpty() ? Optional.empty() : Optional.of(aCanchaInfo((Object[]) filas.get(0)));
    }

    @Override
    public List<HorarioSemanal> horariosDe(Long idCancha) {
        List<?> filas = em.createNativeQuery(
                        "SELECT dia_semana, CAST(hora_inicio AS CHAR), CAST(hora_fin AS CHAR), precio "
                                + "FROM horario_cancha "
                                + "WHERE id_cancha = :idCancha AND activo = 1 "
                                + "ORDER BY dia_semana, hora_inicio")
                .setParameter("idCancha", idCancha.intValue())
                .getResultList();

        List<HorarioSemanal> horarios = new ArrayList<>(filas.size());
        for (Object fila : filas) {
            Object[] f = (Object[]) fila;
            horarios.add(new HorarioSemanal(
                    ((Number) f[0]).intValue(),
                    LocalTime.parse(String.valueOf(f[1]).trim()),
                    LocalTime.parse(String.valueOf(f[2]).trim()),
                    (BigDecimal) f[3]));
        }
        return horarios;
    }

    @Override
    public List<FranjaHoraria> ocupacionEn(Long idCancha, Instant desde, Instant hasta) {
        // Una sola consulta: reservas que retienen cupo + bloqueos operativos.
        List<?> filas = em.createNativeQuery(
                        "SELECT CAST(inicio AS CHAR), CAST(fin AS CHAR) FROM reserva "
                                + "WHERE id_cancha = :idCancha AND estado IN ('PENDIENTE','CONFIRMADA') "
                                + "AND inicio < :hasta AND fin > :desde "
                                + "UNION ALL "
                                + "SELECT CAST(inicio AS CHAR), CAST(fin AS CHAR) FROM bloqueo_cancha "
                                + "WHERE id_cancha = :idCancha AND inicio < :hasta AND fin > :desde")
                .setParameter("idCancha", idCancha.intValue())
                .setParameter("desde", SqlUtc.aLiteral(desde))
                .setParameter("hasta", SqlUtc.aLiteral(hasta))
                .getResultList();

        List<FranjaHoraria> ocupadas = new ArrayList<>(filas.size());
        for (Object fila : filas) {
            Object[] f = (Object[]) fila;
            ocupadas.add(new FranjaHoraria(SqlUtc.aInstant(f[0]), SqlUtc.aInstant(f[1])));
        }
        return ocupadas;
    }

    @Override
    @Transactional
    public boolean existeReservaVigenteSolapada(Long idCancha, Instant inicio, Instant fin) {
        // Lectura bloqueante: garantiza leer la ultima version confirmada y no la
        // instantanea de la transaccion.
        List<?> filas = em.createNativeQuery(
                        "SELECT id_reserva FROM reserva "
                                + "WHERE id_cancha = :idCancha "
                                + "AND estado IN ('PENDIENTE','CONFIRMADA') "
                                + "AND inicio < :fin AND fin > :inicio "
                                + "FOR UPDATE")
                .setParameter("idCancha", idCancha.intValue())
                .setParameter("inicio", SqlUtc.aLiteral(inicio))
                .setParameter("fin", SqlUtc.aLiteral(fin))
                .setMaxResults(1)
                .getResultList();
        return !filas.isEmpty();
    }

    @Override
    public Optional<Reserva> buscarPorClaveIdempotencia(Long idUsuario, String claveIdempotencia) {
        List<?> filas = em.createNativeQuery(
                        "SELECT " + COLUMNAS_RESERVA + RESERVA_CON_COCHERA
                                + " WHERE r.id_usuario = :idUsuario "
                                + "AND r.clave_idempotencia = :clave")
                .setParameter("idUsuario", idUsuario.intValue())
                .setParameter("clave", claveIdempotencia)
                .setMaxResults(1)
                .getResultList();
        return filas.isEmpty() ? Optional.empty() : Optional.of(aReserva((Object[]) filas.get(0)));
    }

    @Override
    @Transactional
    public void moverFranja(Long idReserva, Instant inicio, Instant fin) {
        em.createNativeQuery("UPDATE reserva SET inicio = :inicio, fin = :fin "
                        + "WHERE id_reserva = :id")
                .setParameter("inicio", SqlUtc.aLiteral(inicio))
                .setParameter("fin", SqlUtc.aLiteral(fin))
                .setParameter("id", idReserva)
                .executeUpdate();
        em.createNativeQuery("UPDATE reserva_cochera SET inicio = :inicio, fin = :fin "
                        + "WHERE id_reserva = :id")
                .setParameter("inicio", SqlUtc.aLiteral(inicio))
                .setParameter("fin", SqlUtc.aLiteral(fin))
                .setParameter("id", idReserva)
                .executeUpdate();
        em.createNativeQuery("INSERT INTO historial_reserva "
                        + "(id_reserva, estado_anterior, estado_nuevo, observacion) "
                        + "VALUES (:id, 'PENDIENTE', 'PENDIENTE', "
                        + "'Reserva reprogramada por el titular')")
                .setParameter("id", idReserva)
                .executeUpdate();
    }

    @Override
    public boolean existeReservaVigenteSolapadaExcluyendo(Long idCancha, Instant inicio, Instant fin,
                                                          Long idReservaExcluir) {
        List<?> filas = em.createNativeQuery(
                        "SELECT id_reserva FROM reserva "
                                + "WHERE id_cancha = :idCancha "
                                + "AND id_reserva <> :excluir "
                                + "AND estado IN ('PENDIENTE','CONFIRMADA') "
                                + "AND inicio < :fin AND fin > :inicio "
                                + "FOR UPDATE")
                .setParameter("idCancha", idCancha.intValue())
                .setParameter("excluir", idReservaExcluir)
                .setParameter("inicio", SqlUtc.aLiteral(inicio))
                .setParameter("fin", SqlUtc.aLiteral(fin))
                .setMaxResults(1)
                .getResultList();
        return !filas.isEmpty();
    }

    @Override
    @Transactional
    public boolean existeBloqueoSolapado(Long idCancha, Instant inicio, Instant fin) {
        List<?> filas = em.createNativeQuery(
                        "SELECT id_bloqueo FROM bloqueo_cancha "
                                + "WHERE id_cancha = :idCancha "
                                + "AND inicio < :fin AND fin > :inicio "
                                + "FOR UPDATE")
                .setParameter("idCancha", idCancha.intValue())
                .setParameter("inicio", SqlUtc.aLiteral(inicio))
                .setParameter("fin", SqlUtc.aLiteral(fin))
                .setMaxResults(1)
                .getResultList();
        return !filas.isEmpty();
    }

    @Override
    @Transactional
    public Long registrar(Long idUsuario, Long idCancha, FranjaHoraria franja, BigDecimal monto,
                          BigDecimal montoDescuento, String claveIdempotencia) {
        em.createNativeQuery("INSERT INTO reserva "
                        + "(id_usuario, id_cancha, inicio, fin, monto_cancha, monto_descuento, "
                        + "estado, clave_idempotencia) "
                        + "VALUES (:idUsuario, :idCancha, :inicio, :fin, :monto, :descuento, "
                        + "'PENDIENTE', :clave)")
                .setParameter("idUsuario", idUsuario.intValue())
                .setParameter("idCancha", idCancha.intValue())
                .setParameter("inicio", SqlUtc.aLiteral(franja.inicio()))
                .setParameter("fin", SqlUtc.aLiteral(franja.fin()))
                .setParameter("monto", monto)
                .setParameter("descuento", montoDescuento)
                .setParameter("clave", claveIdempotencia)
                .executeUpdate();

        Long id = ultimoIdGenerado();
        em.createNativeQuery("INSERT INTO historial_reserva "
                        + "(id_reserva, estado_anterior, estado_nuevo, observacion) "
                        + "VALUES (:id, NULL, 'PENDIENTE', :observacion)")
                .setParameter("id", id)
                .setParameter("observacion", "Reserva creada por el titular")
                .executeUpdate();
        return id;
    }

    @Override
    @Transactional
    public Optional<Reserva> buscarParaActualizar(Long idReserva) {
        List<?> filas = em.createNativeQuery(
                        "SELECT " + COLUMNAS_RESERVA + RESERVA_CON_COCHERA
                                + " WHERE r.id_reserva = :id FOR UPDATE")
                .setParameter("id", idReserva)
                .setMaxResults(1)
                .getResultList();
        return filas.isEmpty() ? Optional.empty() : Optional.of(aReserva((Object[]) filas.get(0)));
    }

    @Override
    public Optional<Reserva> buscar(Long idReserva) {
        List<?> filas = em.createNativeQuery(
                        "SELECT " + COLUMNAS_RESERVA + RESERVA_CON_COCHERA
                                + " WHERE r.id_reserva = :id")
                .setParameter("id", idReserva)
                .setMaxResults(1)
                .getResultList();
        return filas.isEmpty() ? Optional.empty() : Optional.of(aReserva((Object[]) filas.get(0)));
    }

    @Override
    @Transactional
    public void cambiarEstado(Long idReserva, Reserva.Estado anterior, Reserva.Estado nuevo, String observacion) {
        em.createNativeQuery("UPDATE reserva SET estado = :estado WHERE id_reserva = :id")
                .setParameter("estado", nuevo.name())
                .setParameter("id", idReserva)
                .executeUpdate();
        em.createNativeQuery("INSERT INTO historial_reserva "
                        + "(id_reserva, estado_anterior, estado_nuevo, observacion) "
                        + "VALUES (:id, :anterior, :nuevo, :observacion)")
                .setParameter("id", idReserva)
                .setParameter("anterior", anterior.name())
                .setParameter("nuevo", nuevo.name())
                .setParameter("observacion", observacion)
                .executeUpdate();
    }

    @Override
    public List<Reserva> listarPorUsuario(Long idUsuario, int limite) {
        List<?> filas = em.createNativeQuery(
                        "SELECT " + COLUMNAS_RESERVA + RESERVA_CON_COCHERA
                                + " WHERE r.id_usuario = :idUsuario ORDER BY r.inicio DESC")
                .setParameter("idUsuario", idUsuario.intValue())
                .setMaxResults(Math.max(1, Math.min(limite, 100)))
                .getResultList();

        List<Reserva> reservas = new ArrayList<>(filas.size());
        for (Object fila : filas) {
            reservas.add(aReserva((Object[]) fila));
        }
        return reservas;
    }

    @Override
    public boolean esPropietarioDeReserva(Long idReserva, Long idUsuario) {
        List<?> filas = em.createNativeQuery("SELECT r.id_reserva FROM reserva r "
                        + "JOIN cancha c ON c.id_cancha = r.id_cancha "
                        + "JOIN establecimiento e ON e.id_establecimiento = c.id_establecimiento "
                        + "JOIN propietario p ON p.id_propietario = e.id_propietario "
                        + "WHERE r.id_reserva = :id AND p.id_usuario = :idUsuario")
                .setParameter("id", idReserva)
                .setParameter("idUsuario", idUsuario.intValue())
                .setMaxResults(1)
                .getResultList();
        return !filas.isEmpty();
    }

    @Override
    public List<Reserva> listarPorPropietario(Long idUsuario, Reserva.Estado estado, int limite) {
        // Subconsulta en lugar de JOIN para poder reutilizar COLUMNAS_RESERVA sin calificar.
        Query consulta = em.createNativeQuery("SELECT " + COLUMNAS_RESERVA + RESERVA_CON_COCHERA
                        + " WHERE r.id_cancha IN (SELECT c.id_cancha FROM cancha c "
                        + "JOIN establecimiento e ON e.id_establecimiento = c.id_establecimiento "
                        + "JOIN propietario p ON p.id_propietario = e.id_propietario "
                        + "WHERE p.id_usuario = :idUsuario)"
                        + (estado == null ? "" : " AND r.estado = :estado")
                        + " ORDER BY r.inicio DESC")
                .setParameter("idUsuario", idUsuario.intValue())
                .setMaxResults(Math.max(1, Math.min(limite, 100)));
        if (estado != null) {
            consulta.setParameter("estado", estado.name());
        }

        List<?> filas = consulta.getResultList();
        List<Reserva> recibidas = new ArrayList<>(filas.size());
        for (Object fila : filas) {
            recibidas.add(aReserva((Object[]) fila));
        }
        return recibidas;
    }

    @Override
    @Transactional
    public Optional<CocheraInfo> bloquearCochera(Long idCochera) {
        List<?> filas = em.createNativeQuery(
                        "SELECT id_cochera, id_establecimiento, nombre, capacidad, tarifa_hora, estado "
                                + "FROM cochera WHERE id_cochera = :id FOR UPDATE")
                .setParameter("id", idCochera.intValue())
                .setMaxResults(1)
                .getResultList();
        return filas.isEmpty() ? Optional.empty() : Optional.of(aCocheraInfo((Object[]) filas.get(0)));
    }

    @Override
    public List<CocheraInfo> cocherasDeEstablecimiento(Long idEstablecimiento) {
        List<?> filas = em.createNativeQuery(
                        "SELECT id_cochera, id_establecimiento, nombre, capacidad, tarifa_hora, estado "
                                + "FROM cochera WHERE id_establecimiento = :idEstablecimiento "
                                + "AND estado = 'ACTIVA' ORDER BY id_cochera")
                .setParameter("idEstablecimiento", idEstablecimiento.intValue())
                .getResultList();

        List<CocheraInfo> cocheras = new ArrayList<>(filas.size());
        for (Object fila : filas) {
            cocheras.add(aCocheraInfo((Object[]) fila));
        }
        return cocheras;
    }

    @Override
    @Transactional
    public int ocupacionCochera(Long idCochera, Instant inicio, Instant fin) {
        // Lectura bloqueante: bajo el lock de la cochera garantiza leer la ultima version
        // confirmada de las reservas de cochera solapadas (no la instantanea de la tx).
        List<?> filas = em.createNativeQuery(
                        "SELECT rc.id_reserva_cochera FROM reserva_cochera rc "
                                + "JOIN reserva r ON r.id_reserva = rc.id_reserva "
                                + "WHERE rc.id_cochera = :idCochera "
                                + "AND r.estado IN ('PENDIENTE','CONFIRMADA') "
                                + "AND rc.inicio < :fin AND rc.fin > :inicio "
                                + "FOR UPDATE")
                .setParameter("idCochera", idCochera.intValue())
                .setParameter("inicio", SqlUtc.aLiteral(inicio))
                .setParameter("fin", SqlUtc.aLiteral(fin))
                .getResultList();
        return filas.size();
    }

    @Override
    @Transactional
    public Long registrarReservaCochera(Long idReserva, Long idCochera, Instant inicio, Instant fin,
                                        BigDecimal monto) {
        em.createNativeQuery("INSERT INTO reserva_cochera "
                        + "(id_reserva, id_cochera, inicio, fin, monto) "
                        + "VALUES (:idReserva, :idCochera, :inicio, :fin, :monto)")
                .setParameter("idReserva", idReserva)
                .setParameter("idCochera", idCochera.intValue())
                .setParameter("inicio", SqlUtc.aLiteral(inicio))
                .setParameter("fin", SqlUtc.aLiteral(fin))
                .setParameter("monto", monto)
                .executeUpdate();
        return ultimoIdGenerado();
    }

    @Override
    @Transactional
    public List<Long> bloquearPendientesConHoldVencido(int holdDefectoMin, int max) {
        // El hold vigente sale del establecimiento de la cancha; si no hay fila de
        // configuracion se aplica el valor por defecto. La antiguedad se mide contra el
        // reloj de la base (`NOW(6)`), el mismo que llena `fecha_creacion` por defecto, para
        // no mezclar marcos horarios (L-04). Las filas se leen con `FOR UPDATE OF r` (solo
        // la reserva) para expirarlas sin bloquear la cancha (que RN-01 bloquea al crear).
        List<?> filas = em.createNativeQuery(
                        "SELECT r.id_reserva FROM reserva r "
                                + "JOIN cancha c ON c.id_cancha = r.id_cancha "
                                + "LEFT JOIN config_establecimiento cfg "
                                + "  ON cfg.id_establecimiento = c.id_establecimiento "
                                + "WHERE r.estado = 'PENDIENTE' "
                                + "AND r.fecha_creacion < TIMESTAMPADD(MINUTE, "
                                + "  -COALESCE(cfg.hold_pago_min, :hold), NOW(6)) "
                                + "AND NOT EXISTS (SELECT 1 FROM pago p "
                                + "  WHERE p.id_reserva = r.id_reserva AND p.estado = 'APROBADO') "
                                + "ORDER BY r.fecha_creacion LIMIT " + Math.max(1, max) + " "
                                + "FOR UPDATE OF r")
                .setParameter("hold", holdDefectoMin)
                .getResultList();

        List<Long> ids = new ArrayList<>(filas.size());
        for (Object fila : filas) {
            ids.add(((Number) fila).longValue());
        }
        return ids;
    }

    @Override
    @Transactional
    public List<Long> bloquearConfirmadasConcluidas(int max) {
        // `inicio`/`fin` se escriben como literales UTC (SqlUtc, ADR-0014): el "ya concluyo"
        // se mide contra el instante actual en UTC (`UTC_TIMESTAMP(6)`), inmune a la zona del
        // servidor. Comparar el literal UTC contra NOW(6) (hora local, L-04) desviaria el
        // cierre por el desfase entre ambos marcos. Sin join, `FOR UPDATE` bloquea solo las
        // filas de `reserva` (no la cancha, que RN-01 bloquea al crear).
        List<?> filas = em.createNativeQuery(
                        "SELECT r.id_reserva FROM reserva r "
                                + "WHERE r.estado = 'CONFIRMADA' "
                                + "AND r.fin < UTC_TIMESTAMP(6) "
                                + "ORDER BY r.fin LIMIT " + Math.max(1, max) + " "
                                + "FOR UPDATE")
                .getResultList();

        List<Long> ids = new ArrayList<>(filas.size());
        for (Object fila : filas) {
            ids.add(((Number) fila).longValue());
        }
        return ids;
    }

    private static CocheraInfo aCocheraInfo(Object[] f) {
        return new CocheraInfo(
                ((Number) f[0]).longValue(),
                ((Number) f[1]).longValue(),
                (String) f[2],
                ((Number) f[3]).intValue(),
                (BigDecimal) f[4],
                (String) f[5]);
    }

    private static CanchaInfo aCanchaInfo(Object[] f) {
        return new CanchaInfo(((Number) f[0]).longValue(), ((Number) f[1]).longValue(),
                (BigDecimal) f[2], (String) f[3]);
    }

    private static Reserva aReserva(Object[] f) {
        Reserva.CocheraReservada cochera = f[9] == null
                ? null
                : new Reserva.CocheraReservada(((Number) f[9]).longValue(), (BigDecimal) f[10]);
        return new Reserva(
                ((Number) f[0]).longValue(),
                ((Number) f[1]).longValue(),
                ((Number) f[2]).longValue(),
                new FranjaHoraria(SqlUtc.aInstant(f[3]), SqlUtc.aInstant(f[4])),
                (BigDecimal) f[5],
                (BigDecimal) f[6],
                Reserva.Estado.valueOf((String) f[7]),
                SqlUtc.aInstant(f[8]),
                cochera);
    }

    /**
     * `LAST_INSERT_ID()` es de sesion; al estar dentro de la misma transaccion
     * corresponde al INSERT inmediatamente anterior de esta conexion.
     */
    private Long ultimoIdGenerado() {
        return ((Number) em.createNativeQuery("SELECT LAST_INSERT_ID()").getSingleResult()).longValue();
    }
}
