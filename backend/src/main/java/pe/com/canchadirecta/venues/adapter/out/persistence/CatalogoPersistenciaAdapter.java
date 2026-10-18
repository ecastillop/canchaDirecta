package pe.com.canchadirecta.venues.adapter.out.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import pe.com.canchadirecta.venues.application.port.out.CatalogoRepositorio;
import pe.com.canchadirecta.venues.domain.model.CatalogoCancha;
import pe.com.canchadirecta.venues.domain.model.FichaEspacio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Consultas del catalogo de canchas con SQL nativo (ADR-0012).
 */
@ApplicationScoped
public class CatalogoPersistenciaAdapter implements CatalogoRepositorio {

    private final EntityManager em;

    @Inject
    public CatalogoPersistenciaAdapter(EntityManager em) {
        this.em = em;
    }

    @Override
    public List<CatalogoCancha.Distrito> distritos(String texto, int limite) {
        String sql = "SELECT d.id_distrito, d.nombre, p.nombre, dep.nombre "
                + "FROM distrito d "
                + "JOIN provincia p ON p.id_provincia = d.id_provincia "
                + "JOIN departamento dep ON dep.id_departamento = p.id_departamento "
                + (texto == null ? "" : "WHERE d.nombre LIKE :texto ")
                + "ORDER BY d.nombre";
        var query = em.createNativeQuery(sql);
        if (texto != null) {
            query.setParameter("texto", "%" + texto + "%");
        }
        query.setMaxResults(Math.max(1, Math.min(limite, 200)));
        List<CatalogoCancha.Distrito> distritos = new ArrayList<>();
        for (Object fila : query.getResultList()) {
            Object[] f = (Object[]) fila;
            distritos.add(new CatalogoCancha.Distrito(
                    ((Number) f[0]).longValue(), (String) f[1], (String) f[2], (String) f[3]));
        }
        return distritos;
    }

    @Override
    public List<CatalogoCancha.Item> deportes() {
        return items("SELECT id_deporte, nombre FROM deporte ORDER BY id_deporte");
    }

    @Override
    public List<CatalogoCancha.Item> caracteristicas() {
        return items("SELECT id_caracteristica, nombre FROM caracteristica ORDER BY nombre");
    }

    private List<CatalogoCancha.Item> items(String sql) {
        List<CatalogoCancha.Item> items = new ArrayList<>();
        for (Object fila : em.createNativeQuery(sql).getResultList()) {
            Object[] f = (Object[]) fila;
            items.add(new CatalogoCancha.Item(((Number) f[0]).longValue(), (String) f[1]));
        }
        return items;
    }

    @Override
    public Optional<CatalogoCancha.Detalle> detalle(Long idCancha) {
        List<?> filas = em.createNativeQuery("SELECT c.id_cancha, c.nombre, c.descripcion, c.capacidad, "
                        + "c.precio_base, c.estado, e.id_establecimiento, e.nombre, e.direccion, "
                        + "e.referencia, d.id_distrito, d.nombre, p.nombre, dep.nombre "
                        + "FROM cancha c "
                        + "JOIN establecimiento e ON e.id_establecimiento = c.id_establecimiento "
                        + "JOIN distrito d ON d.id_distrito = e.id_distrito "
                        + "JOIN provincia p ON p.id_provincia = d.id_provincia "
                        + "JOIN departamento dep ON dep.id_departamento = p.id_departamento "
                        + "WHERE c.id_cancha = :id")
                .setParameter("id", idCancha)
                .setMaxResults(1)
                .getResultList();
        if (filas.isEmpty()) {
            return Optional.empty();
        }
        Object[] f = (Object[]) filas.get(0);
        return Optional.of(new CatalogoCancha.Detalle(
                ((Number) f[0]).longValue(),
                (String) f[1],
                (String) f[2],
                ((Number) f[3]).intValue(),
                (BigDecimal) f[4],
                (String) f[5],
                ((Number) f[6]).longValue(),
                (String) f[7],
                (String) f[8],
                (String) f[9],
                new CatalogoCancha.Distrito(((Number) f[10]).longValue(), (String) f[11],
                        (String) f[12], (String) f[13]),
                deportesDe(idCancha),
                caracteristicasDe(idCancha),
                fotosDe(idCancha),
                reputacionDe(idCancha)));
    }

    @Override
    public Optional<FichaEspacio.Espacio> fichaEspacio(Long idEstablecimiento) {
        List<?> filas = em.createNativeQuery("SELECT e.id_establecimiento, e.nombre, e.direccion, "
                        + "e.referencia, e.estado, d.id_distrito, d.nombre, p.nombre, dep.nombre "
                        + "FROM establecimiento e "
                        + "JOIN distrito d ON d.id_distrito = e.id_distrito "
                        + "JOIN provincia p ON p.id_provincia = d.id_provincia "
                        + "JOIN departamento dep ON dep.id_departamento = p.id_departamento "
                        + "WHERE e.id_establecimiento = :id")
                .setParameter("id", idEstablecimiento)
                .setMaxResults(1)
                .getResultList();
        if (filas.isEmpty()) {
            return Optional.empty();
        }
        Object[] f = (Object[]) filas.get(0);
        List<?> canchasFilas = em.createNativeQuery("SELECT c.id_cancha, c.nombre, c.capacidad, "
                        + "c.precio_base, c.estado FROM cancha c "
                        + "WHERE c.id_establecimiento = :id "
                        + "ORDER BY CASE WHEN c.estado = 'ACTIVA' THEN 0 ELSE 1 END, c.nombre")
                .setParameter("id", idEstablecimiento)
                .getResultList();
        List<Long> idsCancha = new ArrayList<>(canchasFilas.size());
        for (Object fila : canchasFilas) {
            idsCancha.add(((Number) ((Object[]) fila)[0]).longValue());
        }
        Map<Long, List<CatalogoCancha.Item>> deportes = deportesPorCancha(idEstablecimiento);
        FotosEspacio galeria = fotosDelEspacio(idEstablecimiento);
        Map<Long, CatalogoCancha.Reputacion> reputaciones = reputacionPorCancha(idEstablecimiento);

        List<FichaEspacio.CanchaResumen> canchas = new ArrayList<>(canchasFilas.size());
        long totalCalificaciones = 0;
        BigDecimal sumaPuntajes = BigDecimal.ZERO;
        BigDecimal precioDesde = null;
        for (Object fila : canchasFilas) {
            Object[] c = (Object[]) fila;
            Long idCancha = ((Number) c[0]).longValue();
            BigDecimal precio = (BigDecimal) c[3];
            String estado = (String) c[4];
            if ("ACTIVA".equals(estado) && (precioDesde == null || precio.compareTo(precioDesde) < 0)) {
                precioDesde = precio;
            }
            CatalogoCancha.Reputacion reputacion = reputaciones.getOrDefault(idCancha,
                    new CatalogoCancha.Reputacion(0, BigDecimal.ZERO));
            totalCalificaciones += reputacion.total();
            sumaPuntajes = sumaPuntajes.add(reputacion.promedio().multiply(
                    BigDecimal.valueOf(reputacion.total())));
            canchas.add(new FichaEspacio.CanchaResumen(
                    idCancha,
                    (String) c[1],
                    ((Number) c[2]).intValue(),
                    precio,
                    estado,
                    deportes.getOrDefault(idCancha, List.of()),
                    galeria.principales().get(idCancha),
                    reputacion));
        }
        BigDecimal promedio = totalCalificaciones == 0 ? BigDecimal.ZERO
                : sumaPuntajes.divide(BigDecimal.valueOf(totalCalificaciones), 1, RoundingMode.HALF_UP);

        return Optional.of(new FichaEspacio.Espacio(
                ((Number) f[0]).longValue(),
                (String) f[1],
                (String) f[2],
                (String) f[3],
                (String) f[4],
                new CatalogoCancha.Distrito(((Number) f[5]).longValue(), (String) f[6],
                        (String) f[7], (String) f[8]),
                serviciosDelEspacio(idEstablecimiento),
                galeria.fotos(),
                new CatalogoCancha.Reputacion(totalCalificaciones, promedio),
                precioDesde,
                canchas));
    }

    private Map<Long, List<CatalogoCancha.Item>> deportesPorCancha(Long idEstablecimiento) {
        Map<Long, List<CatalogoCancha.Item>> deportes = new HashMap<>();
        List<?> filas = em.createNativeQuery("SELECT cd.id_cancha, d.id_deporte, d.nombre "
                        + "FROM cancha_deporte cd "
                        + "JOIN deporte d ON d.id_deporte = cd.id_deporte "
                        + "JOIN cancha c ON c.id_cancha = cd.id_cancha "
                        + "WHERE c.id_establecimiento = :id ORDER BY d.nombre")
                .setParameter("id", idEstablecimiento)
                .getResultList();
        for (Object fila : filas) {
            Object[] f = (Object[]) fila;
            deportes.computeIfAbsent(((Number) f[0]).longValue(), clave -> new ArrayList<>())
                    .add(new CatalogoCancha.Item(((Number) f[1]).longValue(), (String) f[2]));
        }
        return deportes;
    }

    private List<CatalogoCancha.Caracteristica> serviciosDelEspacio(Long idEstablecimiento) {
        List<CatalogoCancha.Caracteristica> servicios = new ArrayList<>();
        List<?> filas = em.createNativeQuery("SELECT DISTINCT ch.nombre FROM cancha_caracteristica cc "
                        + "JOIN caracteristica ch ON ch.id_caracteristica = cc.id_caracteristica "
                        + "JOIN cancha c ON c.id_cancha = cc.id_cancha "
                        + "WHERE c.id_establecimiento = :id ORDER BY ch.nombre")
                .setParameter("id", idEstablecimiento)
                .getResultList();
        for (Object fila : filas) {
            servicios.add(new CatalogoCancha.Caracteristica((String) fila, null));
        }
        return servicios;
    }

    private FotosEspacio fotosDelEspacio(Long idEstablecimiento) {
        List<CatalogoCancha.Foto> fotos = new ArrayList<>();
        Map<Long, String> principales = new HashMap<>();
        List<?> filas = em.createNativeQuery("SELECT f.id_foto, f.url, f.orden, f.principal, f.id_cancha "
                        + "FROM foto_cancha f JOIN cancha c ON c.id_cancha = f.id_cancha "
                        + "WHERE c.id_establecimiento = :id "
                        + "ORDER BY f.id_cancha, f.principal DESC, f.orden, f.id_foto")
                .setParameter("id", idEstablecimiento)
                .getResultList();
        for (Object fila : filas) {
            Object[] f = (Object[]) fila;
            fotos.add(new CatalogoCancha.Foto(
                    ((Number) f[0]).longValue(), (String) f[1], ((Number) f[2]).intValue(), aBooleano(f[3])));
            principales.putIfAbsent(((Number) f[4]).longValue(), (String) f[1]);
        }
        return new FotosEspacio(fotos, principales);
    }

    private record FotosEspacio(List<CatalogoCancha.Foto> fotos, Map<Long, String> principales) {
    }

    private Map<Long, CatalogoCancha.Reputacion> reputacionPorCancha(Long idEstablecimiento) {
        Map<Long, CatalogoCancha.Reputacion> reputaciones = new HashMap<>();
        List<?> filas = em.createNativeQuery("SELECT r.id_cancha, COUNT(*), AVG(cal.puntaje) "
                        + "FROM calificacion cal JOIN reserva r ON r.id_reserva = cal.id_reserva "
                        + "JOIN cancha c ON c.id_cancha = r.id_cancha "
                        + "WHERE c.id_establecimiento = :id GROUP BY r.id_cancha")
                .setParameter("id", idEstablecimiento)
                .getResultList();
        for (Object fila : filas) {
            Object[] f = (Object[]) fila;
            long total = ((Number) f[1]).longValue();
            BigDecimal promedio = f[2] == null ? BigDecimal.ZERO
                    : new BigDecimal(f[2].toString()).setScale(1, RoundingMode.HALF_UP);
            reputaciones.put(((Number) f[0]).longValue(), new CatalogoCancha.Reputacion(total, promedio));
        }
        return reputaciones;
    }

    private List<CatalogoCancha.Item> deportesDe(Long idCancha) {
        List<CatalogoCancha.Item> items = new ArrayList<>();
        List<?> filas = em.createNativeQuery("SELECT d.id_deporte, d.nombre FROM cancha_deporte cd "
                        + "JOIN deporte d ON d.id_deporte = cd.id_deporte "
                        + "WHERE cd.id_cancha = :id ORDER BY d.nombre")
                .setParameter("id", idCancha)
                .getResultList();
        for (Object fila : filas) {
            Object[] f = (Object[]) fila;
            items.add(new CatalogoCancha.Item(((Number) f[0]).longValue(), (String) f[1]));
        }
        return items;
    }

    private List<CatalogoCancha.Caracteristica> caracteristicasDe(Long idCancha) {
        List<CatalogoCancha.Caracteristica> items = new ArrayList<>();
        List<?> filas = em.createNativeQuery("SELECT c.nombre, cc.valor FROM cancha_caracteristica cc "
                        + "JOIN caracteristica c ON c.id_caracteristica = cc.id_caracteristica "
                        + "WHERE cc.id_cancha = :id ORDER BY c.nombre")
                .setParameter("id", idCancha)
                .getResultList();
        for (Object fila : filas) {
            Object[] f = (Object[]) fila;
            items.add(new CatalogoCancha.Caracteristica((String) f[0], (String) f[1]));
        }
        return items;
    }

    private List<CatalogoCancha.Foto> fotosDe(Long idCancha) {
        List<CatalogoCancha.Foto> fotos = new ArrayList<>();
        List<?> filas = em.createNativeQuery("SELECT id_foto, url, orden, principal FROM foto_cancha "
                        + "WHERE id_cancha = :id ORDER BY orden")
                .setParameter("id", idCancha)
                .getResultList();
        for (Object fila : filas) {
            Object[] f = (Object[]) fila;
            fotos.add(new CatalogoCancha.Foto(
                    ((Number) f[0]).longValue(), (String) f[1], ((Number) f[2]).intValue(), aBooleano(f[3])));
        }
        return fotos;
    }

    private CatalogoCancha.Reputacion reputacionDe(Long idCancha) {
        Object[] f = (Object[]) em.createNativeQuery("SELECT COUNT(*), AVG(cal.puntaje) "
                        + "FROM calificacion cal JOIN reserva r ON r.id_reserva = cal.id_reserva "
                        + "WHERE r.id_cancha = :id")
                .setParameter("id", idCancha)
                .getSingleResult();
        long total = ((Number) f[0]).longValue();
        BigDecimal promedio = f[1] == null ? BigDecimal.ZERO
                : new BigDecimal(f[1].toString()).setScale(1, java.math.RoundingMode.HALF_UP);
        return new CatalogoCancha.Reputacion(total, promedio);
    }

    @Override
    @Transactional
    public void reemplazarDeportes(Long idCancha, List<Long> idsDeporte) {
        em.createNativeQuery("DELETE FROM cancha_deporte WHERE id_cancha = :id")
                .setParameter("id", idCancha)
                .executeUpdate();
        for (Long idDeporte : idsDeporte) {
            em.createNativeQuery("INSERT INTO cancha_deporte (id_cancha, id_deporte) VALUES (:cancha, :deporte)")
                    .setParameter("cancha", idCancha)
                    .setParameter("deporte", idDeporte)
                    .executeUpdate();
        }
    }

    @Override
    @Transactional
    public void reemplazarCaracteristicas(Long idCancha, List<Long> idsCaracteristica) {
        em.createNativeQuery("DELETE FROM cancha_caracteristica WHERE id_cancha = :id")
                .setParameter("id", idCancha)
                .executeUpdate();
        for (Long idCaracteristica : idsCaracteristica) {
            em.createNativeQuery("INSERT INTO cancha_caracteristica (id_cancha, id_caracteristica, valor) "
                            + "VALUES (:cancha, :caracteristica, NULL)")
                    .setParameter("cancha", idCancha)
                    .setParameter("caracteristica", idCaracteristica)
                    .executeUpdate();
        }
    }

    @Override
    public long contarDeportes(List<Long> idsDeporte) {
        return contar("deporte", "id_deporte", idsDeporte);
    }

    @Override
    public long contarCaracteristicas(List<Long> idsCaracteristica) {
        return contar("caracteristica", "id_caracteristica", idsCaracteristica);
    }

    private long contar(String tabla, String columna, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        return ((Number) em.createNativeQuery("SELECT COUNT(*) FROM " + tabla
                        + " WHERE " + columna + " IN (:ids)")
                .setParameter("ids", ids)
                .getSingleResult()).longValue();
    }

    @Override
    public boolean existeOrdenFoto(Long idCancha, int orden) {
        return !em.createNativeQuery("SELECT 1 FROM foto_cancha WHERE id_cancha = :id AND orden = :orden")
                .setParameter("id", idCancha)
                .setParameter("orden", orden)
                .setMaxResults(1)
                .getResultList().isEmpty();
    }

    @Override
    @Transactional
    public Long agregarFoto(Long idCancha, String url, int orden, boolean principal) {
        if (principal) {
            em.createNativeQuery("UPDATE foto_cancha SET principal = 0 WHERE id_cancha = :id")
                    .setParameter("id", idCancha)
                    .executeUpdate();
        }
        em.createNativeQuery("INSERT INTO foto_cancha (id_cancha, url, orden, principal) "
                        + "VALUES (:id, :url, :orden, :principal)")
                .setParameter("id", idCancha)
                .setParameter("url", url)
                .setParameter("orden", orden)
                .setParameter("principal", principal ? 1 : 0)
                .executeUpdate();
        return ((Number) em.createNativeQuery("SELECT LAST_INSERT_ID()").getSingleResult()).longValue();
    }

    @Override
    @Transactional
    public boolean eliminarFoto(Long idCancha, Long idFoto) {
        return em.createNativeQuery("DELETE FROM foto_cancha WHERE id_foto = :foto AND id_cancha = :cancha")
                .setParameter("foto", idFoto)
                .setParameter("cancha", idCancha)
                .executeUpdate() == 1;
    }

    private static boolean aBooleano(Object valor) {
        if (valor instanceof Boolean flag) {
            return flag;
        }
        return valor instanceof Number numero && numero.intValue() == 1;
    }
}
