package pe.com.canchadirecta.identity.application.dto;

/** Datos editables del perfil propio (CU01.5). */
public record ActualizarPerfilCommand(
        String nombres,
        String apellidos,
        String telefono
) {
}
