package pe.com.canchadirecta.identity.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import pe.com.canchadirecta.identity.application.port.in.VerificarCorreoUseCase;
import pe.com.canchadirecta.identity.application.port.out.AuthTokenRepositorio;
import pe.com.canchadirecta.identity.application.port.out.HashTokens;
import pe.com.canchadirecta.identity.application.port.out.UsuarioRepositorio;
import pe.com.canchadirecta.identity.domain.model.TipoToken;
import pe.com.canchadirecta.shared.application.port.out.Reloj;
import pe.com.canchadirecta.shared.domain.DomainException;
import pe.com.canchadirecta.shared.domain.ErrorCodes;

import java.time.Instant;

/** Confirma el correo del usuario consumiendo el token EMAIL_VERIFY (CU01.2). */
@ApplicationScoped
public class VerificarCorreoService implements VerificarCorreoUseCase {

    private final UsuarioRepositorio usuarios;
    private final AuthTokenRepositorio tokens;
    private final HashTokens hashTokens;
    private final Reloj reloj;

    @Inject
    public VerificarCorreoService(UsuarioRepositorio usuarios, AuthTokenRepositorio tokens,
                                  HashTokens hashTokens, Reloj reloj) {
        this.usuarios = usuarios;
        this.tokens = tokens;
        this.hashTokens = hashTokens;
        this.reloj = reloj;
    }

    @Override
    public void verificar(String token) {
        if (token == null || token.isBlank()) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "El token es obligatorio.");
        }
        var info = tokens.buscarPorHash(hashTokens.sha256Hex(token), TipoToken.EMAIL_VERIFY)
                .orElseThrow(() -> new DomainException(ErrorCodes.DATOS_INVALIDOS,
                        "El token no es valido o expiro."));
        Instant ahora = reloj.ahora();
        if (info.estaUsado() || info.estaExpirado(ahora)) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS,
                    "El token no es valido o expiro.");
        }
        tokens.marcarUsado(info.idToken(), ahora);
        usuarios.marcarCorreoVerificado(info.idUsuario());
    }
}
