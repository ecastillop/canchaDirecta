package pe.com.canchadirecta.identity.application.port.in;

import pe.com.canchadirecta.identity.application.dto.IniciarSesionCommand;
import pe.com.canchadirecta.identity.application.dto.TokenEmitidoDto;

/** Caso de uso: autenticar credenciales y emitir tokens. */
public interface AutenticarUseCase {

    TokenEmitidoDto autenticar(IniciarSesionCommand command);
}
