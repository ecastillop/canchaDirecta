package pe.com.canchadirecta.identity.application.dto;

/** Tokens emitidos tras autenticar. El refresh viaja aparte del acceso. */
public record TokenEmitidoDto(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSeconds
) {
    public static TokenEmitidoDto bearer(String accessToken, String refreshToken, long expiresInSeconds) {
        return new TokenEmitidoDto(accessToken, refreshToken, "Bearer", expiresInSeconds);
    }
}
