package pe.com.canchadirecta.venues.domain.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Ficha publica de un establecimiento (espacio deportivo): agrupa sus canchas activas, los
 * servicios que ofrecen en conjunto, las fotos y la reputacion consolidada.
 */
public final class FichaEspacio {

    private FichaEspacio() {
    }

    public record Espacio(
            Long id,
            String nombre,
            String direccion,
            String referencia,
            String estado,
            CatalogoCancha.Distrito distrito,
            List<CatalogoCancha.Caracteristica> servicios,
            List<CatalogoCancha.Foto> fotos,
            CatalogoCancha.Reputacion reputacion,
            BigDecimal precioDesde,
            List<CanchaResumen> canchas
    ) {
    }

    /** Cancha dentro de la ficha del espacio, con sus deportes y su propia reputacion. */
    public record CanchaResumen(
            Long id,
            String nombre,
            int capacidad,
            BigDecimal precioBase,
            String estado,
            List<CatalogoCancha.Item> deportes,
            String fotoPrincipal,
            CatalogoCancha.Reputacion reputacion
    ) {
    }
}
