package pe.com.canchadirecta.helpcenter.adapter.out.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import pe.com.canchadirecta.helpcenter.application.port.out.AyudaRepositorio;
import pe.com.canchadirecta.helpcenter.domain.model.AyudaArticulo;
import pe.com.canchadirecta.helpcenter.domain.model.AyudaCategoria;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Lectura del centro de ayuda con SQL nativo (ADR-0012). */
@ApplicationScoped
public class AyudaPersistenciaAdapter implements AyudaRepositorio {

    private final EntityManager em;

    @Inject
    public AyudaPersistenciaAdapter(EntityManager em) {
        this.em = em;
    }

    @Override
    public List<AyudaCategoria> listar(String texto) {
        List<?> categoriasFilas = em.createNativeQuery(
                        "SELECT id_categoria, nombre, slug FROM ayuda_categoria ORDER BY orden, id_categoria")
                .getResultList();
        Map<Long, List<AyudaArticulo>> articulosPorCategoria = new LinkedHashMap<>();
        String sql = "SELECT a.id_articulo, a.id_categoria, a.pregunta, a.respuesta "
                + "FROM ayuda_articulo a JOIN ayuda_categoria c ON c.id_categoria = a.id_categoria "
                + "WHERE a.estado = 'ACTIVO'";
        if (texto != null) {
            sql += " AND (a.pregunta LIKE :texto OR a.respuesta LIKE :texto)";
        }
        sql += " ORDER BY c.orden, a.orden, a.id_articulo";
        var query = em.createNativeQuery(sql);
        if (texto != null) {
            query.setParameter("texto", patron(texto));
        }
        List<?> articulosFilas = query.getResultList();
        for (Object fila : articulosFilas) {
            Object[] f = (Object[]) fila;
            Long idCategoria = ((Number) f[1]).longValue();
            articulosPorCategoria.computeIfAbsent(idCategoria, clave -> new ArrayList<>())
                    .add(new AyudaArticulo(((Number) f[0]).longValue(), idCategoria,
                            (String) f[2], (String) f[3]));
        }
        List<AyudaCategoria> categorias = new ArrayList<>();
        for (Object fila : categoriasFilas) {
            Object[] f = (Object[]) fila;
            Long id = ((Number) f[0]).longValue();
            List<AyudaArticulo> articulos = articulosPorCategoria.getOrDefault(id, List.of());
            if (!articulos.isEmpty()) {
                categorias.add(new AyudaCategoria(id, (String) f[1], (String) f[2], articulos));
            }
        }
        return categorias;
    }

    private static String patron(String texto) {
        return "%" + texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }
}
