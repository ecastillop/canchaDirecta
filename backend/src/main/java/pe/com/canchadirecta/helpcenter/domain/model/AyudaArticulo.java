package pe.com.canchadirecta.helpcenter.domain.model;

/** Pregunta frecuente del centro de ayuda. */
public record AyudaArticulo(
        Long id,
        Long idCategoria,
        String pregunta,
        String respuesta
) {
}
