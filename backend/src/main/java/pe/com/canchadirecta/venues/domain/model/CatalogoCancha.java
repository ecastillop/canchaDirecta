package pe.com.canchadirecta.venues.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Vista de catalogo de una cancha (CU02.1-CU02.3): datos de la cancha con su establecimiento,
 * distrito, deportes, caracteristicas, fotos y reputacion.
 */
public final class CatalogoCancha {

    private CatalogoCancha() {
    }

    public record Distrito(Long id, String nombre, String provincia, String departamento) {
    }

    public record Item(Long id, String nombre) {
    }

    public record Caracteristica(String nombre, String valor) {
    }

    public record Foto(Long id, String url, int orden, boolean principal) {
    }

    public record Reputacion(long total, BigDecimal promedio) {
    }

    public record Detalle(
            Long id,
            String nombre,
            String descripcion,
            int capacidad,
            BigDecimal precioBase,
            String estado,
            Long idEstablecimiento,
            String establecimiento,
            String direccion,
            String referencia,
            Distrito distrito,
            List<Item> deportes,
            List<Caracteristica> caracteristicas,
            List<Foto> fotos,
            Reputacion reputacion
    ) {
    }

    /** Filtros del listado publico de canchas. */
    public record Filtro(Long idDistrito, Long idEstablecimiento, Long idDeporte, BigDecimal precioMax,
                         String texto, LocalDate fecha, String orden, int limite, int desplazamiento) {
    }

    /** Pagina de canchas con el total que cumple el filtro (para paginacion numerica). */
    public record Pagina(List<Resumen> contenido, long total) {
    }

    /** Fila enriquecida del catalogo: incluye distrito, foto, servicios y reputacion. */
    public record Resumen(
            Long id,
            Long idEstablecimiento,
            String nombre,
            int capacidad,
            BigDecimal precioBase,
            String estado,
            Long idDistrito,
            String distrito,
            String establecimiento,
            String fotoPrincipal,
            BigDecimal promedioCalificacion,
            int totalCalificaciones,
            List<String> deportes
    ) {
    }
}
