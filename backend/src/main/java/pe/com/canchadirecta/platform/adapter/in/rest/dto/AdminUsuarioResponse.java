package pe.com.canchadirecta.platform.adapter.in.rest.dto;

import pe.com.canchadirecta.platform.domain.model.UsuarioAdmin;

import java.time.Instant;
import java.util.List;

/**
 * Usuario con sus roles y estado de cuenta para la administracion.
 */
public record AdminUsuarioResponse(
        Long id,
        String nombreCompleto,
        String correo,
        String telefono,
        String estado,
        List<String> roles,
        Instant fechaRegistro
) {

    public static AdminUsuarioResponse de(UsuarioAdmin usuario) {
        return new AdminUsuarioResponse(usuario.id(), usuario.nombreCompleto(), usuario.correo(),
                usuario.telefono(), usuario.estado(), usuario.roles(), usuario.fechaRegistro());
    }
}
