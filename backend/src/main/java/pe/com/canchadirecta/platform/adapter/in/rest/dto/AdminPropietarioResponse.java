package pe.com.canchadirecta.platform.adapter.in.rest.dto;

import pe.com.canchadirecta.platform.domain.model.PropietarioAdmin;

/**
 * Propietario con su estado de validacion para la administracion.
 */
public record AdminPropietarioResponse(
        Long id,
        Long idUsuario,
        String nombreCompleto,
        String correo,
        String documento,
        String estadoValidacion
) {

    public static AdminPropietarioResponse de(PropietarioAdmin propietario) {
        return new AdminPropietarioResponse(propietario.id(), propietario.idUsuario(),
                propietario.nombreCompleto(), propietario.correo(), propietario.documento(),
                propietario.estadoValidacion().name());
    }
}
