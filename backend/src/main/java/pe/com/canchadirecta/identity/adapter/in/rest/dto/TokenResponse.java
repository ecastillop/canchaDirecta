package pe.com.canchadirecta.identity.adapter.in.rest.dto;

import pe.com.canchadirecta.identity.application.dto.TokenEmitidoDto;

/** Respuesta con los tokens de sesion. */
public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn
) {
    public static TokenResponse desde(TokenEmitidoDto dto) {
        return new TokenResponse(dto.accessToken(), dto.refreshToken(),
                dto.tokenType(), dto.expiresInSeconds());
    }
}
