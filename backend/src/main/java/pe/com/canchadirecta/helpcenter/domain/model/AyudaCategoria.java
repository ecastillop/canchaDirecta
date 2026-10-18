package pe.com.canchadirecta.helpcenter.domain.model;

import java.util.List;

/** Categoria del centro de ayuda con sus preguntas frecuentes publicadas. */
public record AyudaCategoria(
        Long id,
        String nombre,
        String slug,
        List<AyudaArticulo> articulos
) {
}
