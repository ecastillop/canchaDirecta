package pe.com.canchadirecta.venues.adapter.in.rest.dto;

import pe.com.canchadirecta.venues.domain.model.CatalogoCancha;
import pe.com.canchadirecta.venues.domain.model.FichaEspacio;

import java.math.BigDecimal;
import java.util.List;

/**
 * Ficha publica de un espacio deportivo: datos del establecimiento, servicios, fotos,
 * reputacion consolidada y las canchas que lo componen.
 */
public record FichaEspacioResponse(
        Long id,
        String nombre,
        String direccion,
        String referencia,
        String estado,
        CanchaDetalleResponse.Distrito distrito,
        List<CatalogoCancha.Caracteristica> servicios,
        List<CatalogoCancha.Foto> fotos,
        CatalogoCancha.Reputacion reputacion,
        BigDecimal precioDesde,
        List<Cancha> canchas
) {

    public static FichaEspacioResponse de(FichaEspacio.Espacio espacio) {
        return new FichaEspacioResponse(
                espacio.id(), espacio.nombre(), espacio.direccion(), espacio.referencia(),
                espacio.estado(),
                new CanchaDetalleResponse.Distrito(espacio.distrito().id(), espacio.distrito().nombre(),
                        espacio.distrito().provincia(), espacio.distrito().departamento()),
                espacio.servicios(), espacio.fotos(), espacio.reputacion(), espacio.precioDesde(),
                espacio.canchas().stream().map(Cancha::de).toList());
    }

    public record Cancha(
            Long id,
            String nombre,
            int capacidad,
            BigDecimal precioBase,
            String estado,
            List<CatalogoCancha.Item> deportes,
            String fotoPrincipal,
            CatalogoCancha.Reputacion reputacion
    ) {

        static Cancha de(FichaEspacio.CanchaResumen cancha) {
            return new Cancha(cancha.id(), cancha.nombre(), cancha.capacidad(), cancha.precioBase(),
                    cancha.estado(), cancha.deportes(), cancha.fotoPrincipal(), cancha.reputacion());
        }
    }
}
