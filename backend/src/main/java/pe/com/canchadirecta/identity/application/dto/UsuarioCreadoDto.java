package pe.com.canchadirecta.identity.application.dto;

import java.util.Set;

/** Resultado de un registro exitoso. Nunca expone el hash ni datos sensibles. */
public record UsuarioCreadoDto(
        Long id,
        String nombres,
        String apellidos,
        String correo,
        String estado,
        Set<String> roles
) {
}
