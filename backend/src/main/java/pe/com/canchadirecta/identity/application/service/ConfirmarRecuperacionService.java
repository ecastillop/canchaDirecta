package pe.com.canchadirecta.identity.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import pe.com.canchadirecta.identity.application.dto.TokenAuthInfo;
import pe.com.canchadirecta.identity.application.port.in.ConfirmarRecuperacionUseCase;
import pe.com.canchadirecta.identity.application.port.out.AuthTokenRepositorio;
import pe.com.canchadirecta.identity.application.port.out.HashTokens;
import pe.com.canchadirecta.identity.application.port.out.PasswordHasher;
import pe.com.canchadirecta.shared.application.port.out.Reloj;
import pe.com.canchadirecta.identity.application.port.out.SesionRefreshRepositorio;
import pe.com.canchadirecta.identity.application.port.out.UsuarioRepositorio;
import pe.com.canchadirecta.identity.domain.model.PasswordHash;
import pe.com.canchadirecta.identity.domain.model.TipoToken;
import pe.com.canchadirecta.identity.domain.model.Usuario;
import pe.com.canchadirecta.shared.domain.DomainException;
import pe.com.canchadirecta.shared.domain.ErrorCodes;

import java.time.Instant;

/**
 * Cambia la contrasena a partir de un token valido, marca el token como usado y
 * revoca todas las sesiones del usuario (quien tenga la contrasena anterior deja de
 * tener acceso).
 */
@ApplicationScoped
public class ConfirmarRecuperacionService implements ConfirmarRecuperacionUseCase {

    private static final int LARGO_MINIMO_PASSWORD = 8;

    private final UsuarioRepositorio usuarios;
    private final AuthTokenRepositorio tokens;
    private final SesionRefreshRepositorio sesiones;
    private final HashTokens hashTokens;
    private final PasswordHasher hasher;
    private final Reloj reloj;

    @Inject
    public ConfirmarRecuperacionService(UsuarioRepositorio usuarios, AuthTokenRepositorio tokens,
                                        SesionRefreshRepositorio sesiones, HashTokens hashTokens,
                                        PasswordHasher hasher, Reloj reloj) {
        this.usuarios = usuarios;
        this.tokens = tokens;
        this.sesiones = sesiones;
        this.hashTokens = hashTokens;
        this.hasher = hasher;
        this.reloj = reloj;
    }

    @Override
    public void confirmar(String tokenEnClaro, String nuevaPassword, String confirmacionPassword) {
        validarPassword(nuevaPassword, confirmacionPassword);
        if (tokenEnClaro == null || tokenEnClaro.isBlank()) {
            throw tokenInvalido();
        }

        Instant ahora = reloj.ahora();
        TokenAuthInfo info = tokens
                .buscarPorHash(hashTokens.sha256Hex(tokenEnClaro), TipoToken.PASSWORD_RESET)
                .orElseThrow(this::tokenInvalido);

        if (info.estaUsado() || info.estaExpirado(ahora)) {
            throw tokenInvalido();
        }

        Usuario usuario = usuarios.buscarPorId(info.idUsuario())
                .orElseThrow(this::tokenInvalido);

        usuarios.actualizarClaveHash(usuario.id(), new PasswordHash(hasher.hash(nuevaPassword)));
        tokens.marcarUsado(info.idToken(), ahora);
        sesiones.revocarTodasDelUsuario(usuario.id(), ahora);
        usuarios.incrementarTokenVersion(usuario.id());
    }

    private void validarPassword(String nueva, String confirmacion) {
        if (nueva == null || nueva.length() < LARGO_MINIMO_PASSWORD) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS,
                    "La contrasena debe tener al menos " + LARGO_MINIMO_PASSWORD + " caracteres.");
        }
        if (!nueva.equals(confirmacion)) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "La confirmacion no coincide.");
        }
    }

    private DomainException tokenInvalido() {
        return new DomainException(ErrorCodes.DATOS_INVALIDOS,
                "El enlace de recuperacion no es valido o ya expiro.");
    }
}
