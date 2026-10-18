package pe.com.canchadirecta.venues.adapter.out.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import pe.com.canchadirecta.shared.adapter.out.persistence.SqlUtc;
import pe.com.canchadirecta.shared.domain.DomainException;
import pe.com.canchadirecta.shared.domain.ErrorCodes;
import pe.com.canchadirecta.venues.application.port.out.VenuesRepositorio;
import pe.com.canchadirecta.venues.domain.model.BloqueoCancha;
import pe.com.canchadirecta.venues.domain.model.Cancha;
import pe.com.canchadirecta.venues.domain.model.CatalogoCancha;
import pe.com.canchadirecta.venues.domain.model.Cochera;
import pe.com.canchadirecta.venues.domain.model.Establecimiento;
import pe.com.canchadirecta.venues.domain.model.HorarioCancha;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Persistencia de venues con SQL nativo (ADR-0012): evita depender del mapeo JPA de
 * tipos y columnas del esquema heredado. Las escrituras se anotan con `@Transactional`
 * (propagacion REQUIRED), por lo que se unen a la transaccion del caso de uso cuando
 * existe una.
 */
@ApplicationScoped
public class VenuesPersistenciaAdapter implements VenuesRepositorio {

    private final EntityManager em;

    @Inject
    public VenuesPersistenciaAdapter(EntityManager em) {
        this.em = em;
    }

    @Override
    @Transactional
    public Long asegurarPropietario(Long idUsuario, String documentoIdentidad) {
        List<?> existente = em.createNativeQuery(
                        "SELECT id_propietario FROM propietario WHERE id_usuario = :idUsuario")
                .setParameter("idUsuario", idUsuario.intValue())
                .setMaxResults(1)
                .getResultList();
        if (!existente.isEmpty()) {
            return ((Number) existente.get(0)).longValue();
        }

        List<?> deOtro = em.createNativeQuery(
                        "SELECT id_propietario FROM propietario WHERE documento_identidad = :documento")
                .setParameter("documento", documentoIdentidad)
                .setMaxResults(1)
                .getResultList();
        if (!deOtro.isEmpty()) {
            throw new DomainException(ErrorCodes.CONFLICTO,
                    "Ese documento de identidad ya esta registrado por otro propietario.");
        }

        em.createNativeQuery("INSERT INTO propietario (id_usuario, documento_identidad, estado_validacion) "
                        + "VALUES (:idUsuario, :documento, 'PENDIENTE')")
                .setParameter("idUsuario", idUsuario.intValue())
                .setParameter("documento", documentoIdentidad)
                .executeUpdate();
        return ultimoIdGenerado();
    }

    @Override
    @Transactional
    public Long crearEstablecimiento(Long idPropietario, Long idDistrito, String nombre,
                                     String direccion, String referencia) {
        em.createNativeQuery("INSERT INTO establecimiento "
                        + "(id_propietario, id_distrito, nombre, direccion, referencia, estado) "
                        + "VALUES (:propietario, :distrito, :nombre, :direccion, :referencia, 'ACTIVO')")
                .setParameter("propietario", idPropietario.intValue())
                .setParameter("distrito", idDistrito.intValue())
                .setParameter("nombre", nombre)
                .setParameter("direccion", direccion)
                .setParameter("referencia", referencia)
                .executeUpdate();
        return ultimoIdGenerado();
    }

    @Override
    public Optional<Establecimiento> buscarEstablecimiento(Long id) {
        List<?> filas = em.createNativeQuery("SELECT id_establecimiento, id_propietario, id_distrito, "
                        + "nombre, direccion, referencia, estado FROM establecimiento "
                        + "WHERE id_establecimiento = :id")
                .setParameter("id", id.intValue())
                .setMaxResults(1)
                .getResultList();
        if (filas.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(aEstablecimiento((Object[]) filas.get(0)));
    }

    @Override
    public List<Establecimiento> listarEstablecimientosDeUsuario(Long idUsuario) {
        List<?> filas = em.createNativeQuery("SELECT e.id_establecimiento, e.id_propietario, e.id_distrito, "
                        + "e.nombre, e.direccion, e.referencia, e.estado "
                        + "FROM establecimiento e JOIN propietario p ON p.id_propietario = e.id_propietario "
                        + "WHERE p.id_usuario = :idUsuario "
                        + "ORDER BY CASE WHEN e.estado = 'ACTIVO' THEN 0 ELSE 1 END, e.nombre")
                .setParameter("idUsuario", idUsuario.intValue())
                .getResultList();
        List<Establecimiento> establecimientos = new ArrayList<>(filas.size());
        for (Object fila : filas) {
            establecimientos.add(aEstablecimiento((Object[]) fila));
        }
        return establecimientos;
    }

    private static Establecimiento aEstablecimiento(Object[] f) {
        return new Establecimiento(
                ((Number) f[0]).longValue(),
                ((Number) f[1]).longValue(),
                ((Number) f[2]).longValue(),
                (String) f[3],
                (String) f[4],
                (String) f[5],
                Establecimiento.Estado.valueOf((String) f[6]));
    }

    @Override
    @Transactional
    public boolean actualizarEstablecimiento(Long idEstablecimiento, Long idDistrito, String nombre,
                                             String direccion, String referencia) {
        return em.createNativeQuery("UPDATE establecimiento SET id_distrito = :distrito, nombre = :nombre, "
                        + "direccion = :direccion, referencia = :referencia "
                        + "WHERE id_establecimiento = :id")
                .setParameter("distrito", idDistrito.intValue())
                .setParameter("nombre", nombre)
                .setParameter("direccion", direccion)
                .setParameter("referencia", referencia)
                .setParameter("id", idEstablecimiento.intValue())
                .executeUpdate() == 1;
    }

    @Override
    @Transactional
    public boolean desactivarEstablecimiento(Long idEstablecimiento) {
        return em.createNativeQuery("UPDATE establecimiento SET estado = 'INACTIVO' "
                        + "WHERE id_establecimiento = :id AND estado = 'ACTIVO'")
                .setParameter("id", idEstablecimiento.intValue())
                .executeUpdate() == 1;
    }

    @Override
    @Transactional
    public int desactivarCanchasDeEstablecimiento(Long idEstablecimiento) {
        return em.createNativeQuery("UPDATE cancha SET estado = 'INACTIVA' "
                        + "WHERE id_establecimiento = :id AND estado <> 'INACTIVA'")
                .setParameter("id", idEstablecimiento.intValue())
                .executeUpdate();
    }

    @Override
    public boolean tieneReservasVigentesFuturasDeEstablecimiento(Long idEstablecimiento) {
        List<?> filas = em.createNativeQuery("SELECT r.id_reserva FROM reserva r "
                        + "JOIN cancha c ON c.id_cancha = r.id_cancha "
                        + "WHERE c.id_establecimiento = :id "
                        + "AND r.estado IN ('PENDIENTE','CONFIRMADA') AND r.fin > UTC_TIMESTAMP(6)")
                .setParameter("id", idEstablecimiento.intValue())
                .setMaxResults(1)
                .getResultList();
        return !filas.isEmpty();
    }

    @Override
    public boolean esPropietarioDe(Long idEstablecimiento, Long idUsuario) {
        List<?> filas = em.createNativeQuery("SELECT e.id_establecimiento FROM establecimiento e "
                        + "JOIN propietario p ON p.id_propietario = e.id_propietario "
                        + "WHERE e.id_establecimiento = :idEstablecimiento AND p.id_usuario = :idUsuario")
                .setParameter("idEstablecimiento", idEstablecimiento.intValue())
                .setParameter("idUsuario", idUsuario.intValue())
                .setMaxResults(1)
                .getResultList();
        return !filas.isEmpty();
    }

    @Override
    @Transactional
    public Long crearCancha(Long idEstablecimiento, String nombre, String descripcion,
                            int capacidad, BigDecimal precioBase) {
        em.createNativeQuery("INSERT INTO cancha "
                        + "(id_establecimiento, nombre, descripcion, capacidad, precio_base, estado) "
                        + "VALUES (:establecimiento, :nombre, :descripcion, :capacidad, :precio, 'ACTIVA')")
                .setParameter("establecimiento", idEstablecimiento.intValue())
                .setParameter("nombre", nombre)
                .setParameter("descripcion", descripcion)
                .setParameter("capacidad", capacidad)
                .setParameter("precio", precioBase)
                .executeUpdate();
        return ultimoIdGenerado();
    }

    @Override
    public Optional<Cancha> buscarCancha(Long id) {
        List<?> filas = em.createNativeQuery("SELECT id_cancha, id_establecimiento, nombre, "
                        + "capacidad, precio_base, estado FROM cancha WHERE id_cancha = :id")
                .setParameter("id", id.intValue())
                .setMaxResults(1)
                .getResultList();
        return filas.isEmpty() ? Optional.empty() : Optional.of(aCancha((Object[]) filas.get(0)));
    }

    @Override
    @Transactional
    public boolean actualizarCancha(Long idCancha, String nombre, String descripcion, int capacidad,
                                    BigDecimal precioBase) {
        return em.createNativeQuery("UPDATE cancha SET nombre = :nombre, descripcion = :descripcion, "
                        + "capacidad = :capacidad, precio_base = :precio WHERE id_cancha = :id")
                .setParameter("nombre", nombre)
                .setParameter("descripcion", descripcion)
                .setParameter("capacidad", capacidad)
                .setParameter("precio", precioBase)
                .setParameter("id", idCancha.intValue())
                .executeUpdate() == 1;
    }

    @Override
    public boolean existeOtraCanchaConNombre(Long idEstablecimiento, String nombre, Long idCanchaExcluir) {
        List<?> filas = em.createNativeQuery("SELECT id_cancha FROM cancha "
                        + "WHERE id_establecimiento = :idEstablecimiento AND nombre = :nombre "
                        + "AND id_cancha <> :idCancha")
                .setParameter("idEstablecimiento", idEstablecimiento.intValue())
                .setParameter("nombre", nombre)
                .setParameter("idCancha", idCanchaExcluir.intValue())
                .setMaxResults(1)
                .getResultList();
        return !filas.isEmpty();
    }

    @Override
    @Transactional
    public boolean desactivarCancha(Long idCancha) {
        return em.createNativeQuery("UPDATE cancha SET estado = 'INACTIVA' "
                        + "WHERE id_cancha = :id AND estado <> 'INACTIVA'")
                .setParameter("id", idCancha.intValue())
                .executeUpdate() == 1;
    }

    @Override
    public boolean tieneReservasVigentesFuturas(Long idCancha) {
        List<?> filas = em.createNativeQuery("SELECT id_reserva FROM reserva "
                        + "WHERE id_cancha = :id AND estado IN ('PENDIENTE','CONFIRMADA') "
                        + "AND fin > UTC_TIMESTAMP(6)")
                .setParameter("id", idCancha.intValue())
                .setMaxResults(1)
                .getResultList();
        return !filas.isEmpty();
    }

    @Override
    @Transactional
    public Optional<Cancha> bloquearCancha(Long idCancha) {
        List<?> filas = em.createNativeQuery("SELECT id_cancha, id_establecimiento, nombre, "
                        + "capacidad, precio_base, estado FROM cancha WHERE id_cancha = :id FOR UPDATE")
                .setParameter("id", idCancha.intValue())
                .setMaxResults(1)
                .getResultList();
        return filas.isEmpty() ? Optional.empty() : Optional.of(aCancha((Object[]) filas.get(0)));
    }

    @Override
    public boolean esPropietarioDeCancha(Long idCancha, Long idUsuario) {
        List<?> filas = em.createNativeQuery("SELECT c.id_cancha FROM cancha c "
                        + "JOIN establecimiento e ON e.id_establecimiento = c.id_establecimiento "
                        + "JOIN propietario p ON p.id_propietario = e.id_propietario "
                        + "WHERE c.id_cancha = :idCancha AND p.id_usuario = :idUsuario")
                .setParameter("idCancha", idCancha.intValue())
                .setParameter("idUsuario", idUsuario.intValue())
                .setMaxResults(1)
                .getResultList();
        return !filas.isEmpty();
    }

    @Override
    public boolean esGestorDeCancha(Long idCancha, Long idUsuario) {
        List<?> filas = em.createNativeQuery("SELECT c.id_cancha FROM cancha c "
                        + "JOIN establecimiento e ON e.id_establecimiento = c.id_establecimiento "
                        + "WHERE c.id_cancha = :idCancha AND ("
                        + "e.id_propietario = (SELECT p.id_propietario FROM propietario p "
                        + "WHERE p.id_usuario = :idUsuario) "
                        + "OR EXISTS (SELECT 1 FROM operador_establecimiento oe "
                        + "WHERE oe.id_usuario = :idUsuario "
                        + "AND oe.id_establecimiento = e.id_establecimiento AND oe.activo = 1))")
                .setParameter("idCancha", idCancha.intValue())
                .setParameter("idUsuario", idUsuario.intValue())
                .setMaxResults(1)
                .getResultList();
        return !filas.isEmpty();
    }

    @Override
    public List<HorarioCancha> listarHorarios(Long idCancha) {
        List<?> filas = em.createNativeQuery("SELECT id_horario, dia_semana, CAST(hora_inicio AS CHAR), "
                        + "CAST(hora_fin AS CHAR), precio, activo FROM horario_cancha "
                        + "WHERE id_cancha = :idCancha AND activo = 1 "
                        + "ORDER BY dia_semana, hora_inicio")
                .setParameter("idCancha", idCancha.intValue())
                .getResultList();

        List<HorarioCancha> horarios = new ArrayList<>(filas.size());
        for (Object fila : filas) {
            Object[] f = (Object[]) fila;
            horarios.add(new HorarioCancha(
                    ((Number) f[0]).longValue(),
                    idCancha,
                    ((Number) f[1]).intValue(),
                    LocalTime.parse(String.valueOf(f[2]).trim()),
                    LocalTime.parse(String.valueOf(f[3]).trim()),
                    (BigDecimal) f[4],
                    aBooleano(f[5])));
        }
        return horarios;
    }

    /** Un `TINYINT(1)` llega como `Boolean` o como `Number` segun el driver: se normaliza. */
    private static boolean aBooleano(Object valor) {
        if (valor instanceof Boolean flag) {
            return flag;
        }
        return valor instanceof Number numero && numero.intValue() == 1;
    }

    @Override
    @Transactional
    public Long crearHorario(HorarioCancha horario) {
        em.createNativeQuery("INSERT INTO horario_cancha "
                        + "(id_cancha, dia_semana, hora_inicio, hora_fin, precio, activo) "
                        + "VALUES (:idCancha, :dia, :inicio, :fin, :precio, 1)")
                .setParameter("idCancha", horario.idCancha().intValue())
                .setParameter("dia", horario.diaSemana())
                .setParameter("inicio", horario.horaInicio().toString())
                .setParameter("fin", horario.horaFin().toString())
                .setParameter("precio", horario.precio())
                .executeUpdate();
        return ultimoIdGenerado();
    }

    @Override
    @Transactional
    public boolean desactivarHorario(Long idCancha, Long idHorario) {
        return em.createNativeQuery("UPDATE horario_cancha SET activo = 0 "
                        + "WHERE id_horario = :idHorario AND id_cancha = :idCancha AND activo = 1")
                .setParameter("idHorario", idHorario.intValue())
                .setParameter("idCancha", idCancha.intValue())
                .executeUpdate() == 1;
    }

    @Override
    public List<BloqueoCancha> listarBloqueos(Long idCancha, Instant desde, Instant hasta) {
        List<?> filas = em.createNativeQuery("SELECT id_bloqueo, CAST(inicio AS CHAR), "
                        + "CAST(fin AS CHAR), motivo FROM bloqueo_cancha "
                        + "WHERE id_cancha = :idCancha AND inicio < :hasta AND fin > :desde "
                        + "ORDER BY inicio")
                .setParameter("idCancha", idCancha.intValue())
                .setParameter("desde", SqlUtc.aLiteral(desde))
                .setParameter("hasta", SqlUtc.aLiteral(hasta))
                .getResultList();

        List<BloqueoCancha> bloqueos = new ArrayList<>(filas.size());
        for (Object fila : filas) {
            Object[] f = (Object[]) fila;
            bloqueos.add(new BloqueoCancha(
                    ((Number) f[0]).longValue(),
                    idCancha,
                    SqlUtc.aInstant(f[1]),
                    SqlUtc.aInstant(f[2]),
                    (String) f[3]));
        }
        return bloqueos;
    }

    @Override
    @Transactional
    public Long crearBloqueo(BloqueoCancha bloqueo) {
        em.createNativeQuery("INSERT INTO bloqueo_cancha (id_cancha, inicio, fin, motivo) "
                        + "VALUES (:idCancha, :inicio, :fin, :motivo)")
                .setParameter("idCancha", bloqueo.idCancha().intValue())
                .setParameter("inicio", SqlUtc.aLiteral(bloqueo.inicio()))
                .setParameter("fin", SqlUtc.aLiteral(bloqueo.fin()))
                .setParameter("motivo", bloqueo.motivo())
                .executeUpdate();
        return ultimoIdGenerado();
    }

    @Override
    @Transactional
    public boolean eliminarBloqueo(Long idCancha, Long idBloqueo) {
        return em.createNativeQuery("DELETE FROM bloqueo_cancha "
                        + "WHERE id_bloqueo = :idBloqueo AND id_cancha = :idCancha")
                .setParameter("idBloqueo", idBloqueo)
                .setParameter("idCancha", idCancha.intValue())
                .executeUpdate() == 1;
    }

    @Override
    public boolean existeBloqueoSolapado(Long idCancha, Instant inicio, Instant fin) {
        List<?> filas = em.createNativeQuery("SELECT id_bloqueo FROM bloqueo_cancha "
                        + "WHERE id_cancha = :idCancha AND inicio < :fin AND fin > :inicio")
                .setParameter("idCancha", idCancha.intValue())
                .setParameter("inicio", SqlUtc.aLiteral(inicio))
                .setParameter("fin", SqlUtc.aLiteral(fin))
                .setMaxResults(1)
                .getResultList();
        return !filas.isEmpty();
    }

    @Override
    public boolean existeReservaVigenteSolapada(Long idCancha, Instant inicio, Instant fin) {
        // El esquema es compartido por los modulos del monolito; esta consulta replica la
        // comprobacion de RN-03 para no bloquear una franja que ya tiene reserva.
        List<?> filas = em.createNativeQuery("SELECT id_reserva FROM reserva "
                        + "WHERE id_cancha = :idCancha AND estado IN ('PENDIENTE','CONFIRMADA') "
                        + "AND inicio < :fin AND fin > :inicio")
                .setParameter("idCancha", idCancha.intValue())
                .setParameter("inicio", SqlUtc.aLiteral(inicio))
                .setParameter("fin", SqlUtc.aLiteral(fin))
                .setMaxResults(1)
                .getResultList();
        return !filas.isEmpty();
    }

    @Override
    public List<Cancha> listarCanchas(Long idDistrito, int limite) {
        String sql = "SELECT c.id_cancha, c.id_establecimiento, c.nombre, c.capacidad, c.precio_base, c.estado "
                + "FROM cancha c JOIN establecimiento e ON e.id_establecimiento = c.id_establecimiento "
                + "WHERE c.estado = 'ACTIVA' AND e.estado = 'ACTIVO' "
                + (idDistrito == null ? "" : "AND e.id_distrito = :idDistrito ")
                + "ORDER BY c.id_cancha";
        var query = em.createNativeQuery(sql);
        if (idDistrito != null) {
            query.setParameter("idDistrito", idDistrito.intValue());
        }
        query.setMaxResults(Math.max(1, Math.min(limite, 100)));

        List<Cancha> canchas = new ArrayList<>();
        for (Object fila : query.getResultList()) {
            canchas.add(aCancha((Object[]) fila));
        }
        return canchas;
    }

    @Override
    public List<Long> listarIdsFavoritos(Long idUsuario) {
        List<?> filas = em.createNativeQuery("SELECT id_cancha FROM favorito WHERE id_usuario = :idUsuario "
                        + "ORDER BY fecha_registro DESC")
                .setParameter("idUsuario", idUsuario.intValue())
                .getResultList();
        List<Long> ids = new ArrayList<>(filas.size());
        for (Object fila : filas) {
            ids.add(((Number) fila).longValue());
        }
        return ids;
    }

    @Override
    @Transactional
    public void agregarFavorito(Long idUsuario, Long idCancha) {
        em.createNativeQuery("INSERT IGNORE INTO favorito (id_usuario, id_cancha) "
                        + "VALUES (:idUsuario, :idCancha)")
                .setParameter("idUsuario", idUsuario.intValue())
                .setParameter("idCancha", idCancha.intValue())
                .executeUpdate();
    }

    @Override
    @Transactional
    public void quitarFavorito(Long idUsuario, Long idCancha) {
        em.createNativeQuery("DELETE FROM favorito WHERE id_usuario = :idUsuario AND id_cancha = :idCancha")
                .setParameter("idUsuario", idUsuario.intValue())
                .setParameter("idCancha", idCancha.intValue())
                .executeUpdate();
    }

    @Override
    public List<CatalogoCancha.Resumen> buscarCanchas(CatalogoCancha.Filtro filtro) {
        StringBuilder sql = new StringBuilder(
                "SELECT c.id_cancha, c.id_establecimiento, c.nombre, c.capacidad, c.precio_base, c.estado, "
                        + "e.id_distrito, d.nombre, e.nombre, "
                        + "(SELECT f.url FROM foto_cancha f WHERE f.id_cancha = c.id_cancha "
                        + "ORDER BY f.principal DESC, f.orden ASC LIMIT 1), "
                        + "COALESCE((SELECT AVG(cal.puntaje) FROM calificacion cal "
                        + "JOIN reserva r ON r.id_reserva = cal.id_reserva WHERE r.id_cancha = c.id_cancha), 0), "
                        + "(SELECT COUNT(*) FROM calificacion cal "
                        + "JOIN reserva r ON r.id_reserva = cal.id_reserva WHERE r.id_cancha = c.id_cancha), "
                        + "(SELECT GROUP_CONCAT(dep.nombre ORDER BY dep.nombre SEPARATOR ',') FROM cancha_deporte cd "
                        + "JOIN deporte dep ON dep.id_deporte = cd.id_deporte WHERE cd.id_cancha = c.id_cancha) "
                        + "FROM cancha c "
                        + "JOIN establecimiento e ON e.id_establecimiento = c.id_establecimiento "
                        + "JOIN distrito d ON d.id_distrito = e.id_distrito "
                        + "WHERE c.estado = 'ACTIVA' AND e.estado = 'ACTIVO'");
        if (filtro.idDistrito() != null) {
            sql.append(" AND e.id_distrito = :idDistrito");
        }
        if (filtro.idEstablecimiento() != null) {
            sql.append(" AND c.id_establecimiento = :idEstablecimiento");
        }
        if (filtro.idDeporte() != null) {
            sql.append(" AND EXISTS (SELECT 1 FROM cancha_deporte cd WHERE cd.id_cancha = c.id_cancha "
                    + "AND cd.id_deporte = :idDeporte)");
        }
        if (filtro.precioMax() != null) {
            sql.append(" AND c.precio_base <= :precioMax");
        }
        if (filtro.texto() != null) {
            sql.append(" AND (c.nombre LIKE :texto OR e.nombre LIKE :texto)");
        }
        sql.append(" ORDER BY c.id_cancha");

        var query = em.createNativeQuery(sql.toString());
        if (filtro.idDistrito() != null) {
            query.setParameter("idDistrito", filtro.idDistrito().intValue());
        }
        if (filtro.idEstablecimiento() != null) {
            query.setParameter("idEstablecimiento", filtro.idEstablecimiento().intValue());
        }
        if (filtro.idDeporte() != null) {
            query.setParameter("idDeporte", filtro.idDeporte().intValue());
        }
        if (filtro.precioMax() != null) {
            query.setParameter("precioMax", filtro.precioMax());
        }
        if (filtro.texto() != null) {
            query.setParameter("texto", "%" + filtro.texto() + "%");
        }
        query.setMaxResults(Math.max(1, Math.min(filtro.limite(), 100)));
        query.setFirstResult(filtro.desplazamiento());

        List<CatalogoCancha.Resumen> canchas = new ArrayList<>();
        for (Object fila : query.getResultList()) {
            canchas.add(aResumen((Object[]) fila));
        }
        return canchas;
    }

    @Override
    public long contarCanchas(CatalogoCancha.Filtro filtro) {
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM cancha c JOIN establecimiento e ON e.id_establecimiento = c.id_establecimiento "
                        + "WHERE c.estado = 'ACTIVA' AND e.estado = 'ACTIVO'");
        if (filtro.idDistrito() != null) {
            sql.append(" AND e.id_distrito = :idDistrito");
        }
        if (filtro.idEstablecimiento() != null) {
            sql.append(" AND c.id_establecimiento = :idEstablecimiento");
        }
        if (filtro.idDeporte() != null) {
            sql.append(" AND EXISTS (SELECT 1 FROM cancha_deporte cd WHERE cd.id_cancha = c.id_cancha "
                    + "AND cd.id_deporte = :idDeporte)");
        }
        if (filtro.precioMax() != null) {
            sql.append(" AND c.precio_base <= :precioMax");
        }
        if (filtro.texto() != null) {
            sql.append(" AND (c.nombre LIKE :texto OR e.nombre LIKE :texto)");
        }

        var query = em.createNativeQuery(sql.toString());
        if (filtro.idDistrito() != null) {
            query.setParameter("idDistrito", filtro.idDistrito().intValue());
        }
        if (filtro.idEstablecimiento() != null) {
            query.setParameter("idEstablecimiento", filtro.idEstablecimiento().intValue());
        }
        if (filtro.idDeporte() != null) {
            query.setParameter("idDeporte", filtro.idDeporte().intValue());
        }
        if (filtro.precioMax() != null) {
            query.setParameter("precioMax", filtro.precioMax());
        }
        if (filtro.texto() != null) {
            query.setParameter("texto", "%" + filtro.texto() + "%");
        }
        return ((Number) query.getSingleResult()).longValue();
    }

    private static Cancha aCancha(Object[] f) {
        return new Cancha(
                ((Number) f[0]).longValue(),
                ((Number) f[1]).longValue(),
                (String) f[2],
                ((Number) f[3]).intValue(),
                (BigDecimal) f[4],
                Cancha.Estado.valueOf((String) f[5]));
    }

    private static CatalogoCancha.Resumen aResumen(Object[] f) {
        String deportes = (String) f[12];
        List<String> nombresDeporte = deportes == null || deportes.isBlank()
                ? List.of()
                : java.util.Arrays.stream(deportes.split(",")).map(String::trim).toList();
        return new CatalogoCancha.Resumen(
                ((Number) f[0]).longValue(),
                ((Number) f[1]).longValue(),
                (String) f[2],
                ((Number) f[3]).intValue(),
                (BigDecimal) f[4],
                (String) f[5],
                ((Number) f[6]).longValue(),
                (String) f[7],
                (String) f[8],
                (String) f[9],
                new BigDecimal(((Number) f[10]).toString()),
                ((Number) f[11]).intValue(),
                nombresDeporte);
    }

    @Override
    @Transactional
    public Long crearCochera(Cochera cochera) {
        em.createNativeQuery("INSERT INTO cochera "
                        + "(id_establecimiento, nombre, capacidad, tarifa_hora, estado) "
                        + "VALUES (:establecimiento, :nombre, :capacidad, :tarifa, 'ACTIVA')")
                .setParameter("establecimiento", cochera.idEstablecimiento().intValue())
                .setParameter("nombre", cochera.nombre())
                .setParameter("capacidad", cochera.capacidad())
                .setParameter("tarifa", cochera.tarifaHora())
                .executeUpdate();
        return ultimoIdGenerado();
    }

    @Override
    public List<Cochera> listarCocheras(Long idEstablecimiento) {
        List<?> filas = em.createNativeQuery("SELECT id_cochera, id_establecimiento, nombre, "
                        + "capacidad, tarifa_hora, estado FROM cochera "
                        + "WHERE id_establecimiento = :idEstablecimiento "
                        + "ORDER BY (estado = 'ACTIVA') DESC, id_cochera")
                .setParameter("idEstablecimiento", idEstablecimiento.intValue())
                .getResultList();

        List<Cochera> cocheras = new ArrayList<>(filas.size());
        for (Object fila : filas) {
            cocheras.add(aCochera((Object[]) fila));
        }
        return cocheras;
    }

    @Override
    public Optional<Cochera> buscarCochera(Long id) {
        List<?> filas = em.createNativeQuery("SELECT id_cochera, id_establecimiento, nombre, "
                        + "capacidad, tarifa_hora, estado FROM cochera WHERE id_cochera = :id")
                .setParameter("id", id.intValue())
                .setMaxResults(1)
                .getResultList();
        return filas.isEmpty() ? Optional.empty() : Optional.of(aCochera((Object[]) filas.get(0)));
    }

    @Override
    @Transactional
    public boolean desactivarCochera(Long idEstablecimiento, Long idCochera) {
        return em.createNativeQuery("UPDATE cochera SET estado = 'INACTIVA' "
                        + "WHERE id_cochera = :idCochera AND id_establecimiento = :idEstablecimiento "
                        + "AND estado = 'ACTIVA'")
                .setParameter("idCochera", idCochera.intValue())
                .setParameter("idEstablecimiento", idEstablecimiento.intValue())
                .executeUpdate() == 1;
    }

    private static Cochera aCochera(Object[] f) {
        return new Cochera(
                ((Number) f[0]).longValue(),
                ((Number) f[1]).longValue(),
                (String) f[2],
                ((Number) f[3]).intValue(),
                (BigDecimal) f[4],
                Cochera.Estado.valueOf((String) f[5]));
    }

    /**
     * `LAST_INSERT_ID()` es de sesion (conexion) y estamos dentro de la misma
     * transaccion, por lo que devuelve la clave del INSERT inmediatamente anterior.
     */
    private Long ultimoIdGenerado() {
        return ((Number) em.createNativeQuery("SELECT LAST_INSERT_ID()").getSingleResult()).longValue();
    }
}
