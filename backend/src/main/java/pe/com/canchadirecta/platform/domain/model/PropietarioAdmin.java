package pe.com.canchadirecta.platform.domain.model;

import java.util.List;

/**
 * Propietario visto por la administracion de plataforma (CU06.2), con los datos de contacto de
 * su usuario.
 */
public record PropietarioAdmin(
        Long id,
        Long idUsuario,
        String nombreCompleto,
        String correo,
        String documento,
        Estado estadoValidacion
) {

    /** Estados admitidos por `ck_propietario_estado` (V1). */
    public enum Estado {
        PENDIENTE, VALIDADO, RECHAZADO;

        public static Estado de(String valor) {
            for (Estado candidato : List.of(values())) {
                if (candidato.name().equalsIgnoreCase(valor == null ? "" : valor.trim())) {
                    return candidato;
                }
            }
            return null;
        }
    }
}
