package pe.com.canchadirecta.identity.application.port.out;

import pe.com.canchadirecta.identity.application.dto.TokenAuthInfo;
import pe.com.canchadirecta.identity.domain.model.TipoToken;

import java.time.Instant;
import java.util.Optional;

/** Puerto de salida: tokens de un solo uso (verificacion de correo, reset de clave). */
public interface AuthTokenRepositorio {

    /** Persiste el token (solo el hash) y descarta los anteriores del mismo tipo. */
    void emitir(Long idUsuario, TipoToken tipo, String tokenHash, Instant expiraEn);

    Optional<TokenAuthInfo> buscarPorHash(String tokenHash, TipoToken tipo);

    void marcarUsado(Long idToken, Instant usadoEn);
}
