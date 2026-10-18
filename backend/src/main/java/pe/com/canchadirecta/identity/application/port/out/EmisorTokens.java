package pe.com.canchadirecta.identity.application.port.out;

import pe.com.canchadirecta.identity.application.dto.TokenEmitidoDto;
import pe.com.canchadirecta.identity.domain.model.Usuario;

/**
 * Puerto de salida: emision de tokens de acceso (JWT) y de refresco (opaco).
 * El token de refresco se persiste hasheado; nunca se guarda en claro.
 */
public interface EmisorTokens {

    TokenEmitidoDto emitir(Usuario usuario);
}
