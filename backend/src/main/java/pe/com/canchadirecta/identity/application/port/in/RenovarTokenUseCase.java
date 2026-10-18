package pe.com.canchadirecta.identity.application.port.in;

import pe.com.canchadirecta.identity.application.dto.TokenEmitidoDto;

/** Caso de uso: renovar el token de acceso con rotacion del refresco. */
public interface RenovarTokenUseCase {

    TokenEmitidoDto renovar(String refreshToken);
}
