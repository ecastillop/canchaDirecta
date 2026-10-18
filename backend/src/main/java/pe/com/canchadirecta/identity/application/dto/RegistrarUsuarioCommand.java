package pe.com.canchadirecta.identity.application.dto;

/** Comando de aplicacion para registrar un usuario (no es un DTO REST). */
public record RegistrarUsuarioCommand(
        String nombres,
        String apellidos,
        String correo,
        String telefono,
        String password,
        String confirmacionPassword
) {
}
