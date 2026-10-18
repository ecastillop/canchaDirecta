package pe.com.canchadirecta.identity.application.dto;

/** Comando de aplicacion para iniciar sesion. */
public record IniciarSesionCommand(
        String correo,
        String password
) {
}
