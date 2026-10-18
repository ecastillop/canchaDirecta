package pe.com.canchadirecta.venues.adapter.in.rest.dto;

import pe.com.canchadirecta.venues.domain.model.CatalogoCancha;

import java.math.BigDecimal;
import java.util.List;

/**
 * Ficha publica de una cancha con establecimiento, distrito, atributos, fotos y reputacion.
 */
public record CanchaDetalleResponse(
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

    public static CanchaDetalleResponse de(CatalogoCancha.Detalle detalle) {
        return new CanchaDetalleResponse(detalle.id(), detalle.nombre(), detalle.descripcion(),
                detalle.capacidad(), detalle.precioBase(), detalle.estado(),
                detalle.idEstablecimiento(), detalle.establecimiento(), detalle.direccion(),
                detalle.referencia(),
                new Distrito(detalle.distrito().id(), detalle.distrito().nombre(),
                        detalle.distrito().provincia(), detalle.distrito().departamento()),
                detalle.deportes().stream().map(d -> new Item(d.id(), d.nombre())).toList(),
                detalle.caracteristicas().stream()
                        .map(c -> new Caracteristica(c.nombre(), c.valor())).toList(),
                detalle.fotos().stream()
                        .map(f -> new Foto(f.id(), f.url(), f.orden(), f.principal())).toList(),
                new Reputacion(detalle.reputacion().total(), detalle.reputacion().promedio()));
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
}
