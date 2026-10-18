package pe.com.canchadirecta.identity.adapter.in.rest.dto;

import pe.com.canchadirecta.identity.application.dto.UsuarioCreadoDto;

import java.util.Set;

/** Respuesta de un usuario registrado (sin datos sensibles). */
public record UsuarioResponse(
        Long id,
        String nombres,
        String apellidos,
        String correo,
        String estado,
        Set<String> roles
) {
    public static UsuarioResponse desde(UsuarioCreadoDto dto) {
        return new UsuarioResponse(dto.id(), dto.nombres(), dto.apellidos(),
                dto.correo(), dto.estado(), dto.roles());
    }
}
