package pe.com.canchadirecta.identity.application.port.in;

import pe.com.canchadirecta.identity.application.dto.RegistrarUsuarioCommand;
import pe.com.canchadirecta.identity.application.dto.UsuarioCreadoDto;

/** Caso de uso: registrar una cuenta de usuario. */
public interface RegistrarUsuarioUseCase {

    UsuarioCreadoDto registrar(RegistrarUsuarioCommand command);
}
