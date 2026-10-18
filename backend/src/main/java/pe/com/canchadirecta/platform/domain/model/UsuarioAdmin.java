package pe.com.canchadirecta.platform.domain.model;

import java.time.Instant;
import java.util.List;

/**
 * Usuario visto por la administracion de plataforma (CU06.1), con sus roles y estado de cuenta.
 */
public record UsuarioAdmin(
        Long id,
        String nombreCompleto,
        String correo,
        String telefono,
        String estado,
        List<String> roles,
        Instant fechaRegistro
) {

    /** Estados admitidos por `ck_usuario_estado` (V1). */
    public enum Estado {
        ACTIVO, INACTIVO, BLOQUEADO
    }
}
