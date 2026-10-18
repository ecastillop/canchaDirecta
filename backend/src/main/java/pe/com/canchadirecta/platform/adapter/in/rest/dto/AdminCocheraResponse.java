package pe.com.canchadirecta.platform.adapter.in.rest.dto;

import pe.com.canchadirecta.platform.domain.model.CocheraAdmin;

import java.math.BigDecimal;

/**
 * Cochera con su establecimiento para la administracion.
 */
public record AdminCocheraResponse(
        Long id,
        Long idEstablecimiento,
        String establecimiento,
        String nombre,
        int capacidad,
        BigDecimal tarifaHora,
        String estado
) {

    public static AdminCocheraResponse de(CocheraAdmin cochera) {
        return new AdminCocheraResponse(cochera.id(), cochera.idEstablecimiento(),
                cochera.establecimiento(), cochera.nombre(), cochera.capacidad(),
                cochera.tarifaHora(), cochera.estado());
    }
}
