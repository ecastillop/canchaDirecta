package pe.com.canchadirecta.identity.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import pe.com.canchadirecta.identity.application.dto.SesionRefreshInfo;
import pe.com.canchadirecta.identity.application.dto.TokenEmitidoDto;
import pe.com.canchadirecta.identity.application.port.in.RenovarTokenUseCase;
import pe.com.canchadirecta.identity.application.port.out.EmisorTokens;
import pe.com.canchadirecta.identity.application.port.out.HashTokens;
import pe.com.canchadirecta.shared.application.port.out.Reloj;
import pe.com.canchadirecta.identity.application.port.out.SesionRefreshRepositorio;
import pe.com.canchadirecta.identity.application.port.out.UsuarioRepositorio;
import pe.com.canchadirecta.identity.domain.model.Usuario;
import pe.com.canchadirecta.shared.domain.DomainException;
import pe.com.canchadirecta.shared.domain.ErrorCodes;

import java.time.Instant;
import java.util.Optional;

/**
 * Renovacion con rotacion: el refresco usado se revoca y se emite uno nuevo.
 * Si se detecta reutilizacion de un refresco ya revocado, se revocan TODAS las
 * sesiones del usuario (defensa ante robo de token).
 */
@ApplicationScoped
public class RenovarTokenService implements RenovarTokenUseCase {

    private final SesionRefreshRepositorio sesiones;
    private final UsuarioRepositorio usuarios;
    private final HashTokens hashTokens;
    private final EmisorTokens emisorTokens;
    private final Reloj reloj;

    @Inject
    public RenovarTokenService(SesionRefreshRepositorio sesiones, UsuarioRepositorio usuarios,
                               HashTokens hashTokens, EmisorTokens emisorTokens, Reloj reloj) {
        this.sesiones = sesiones;
        this.usuarios = usuarios;
        this.hashTokens = hashTokens;
        this.emisorTokens = emisorTokens;
        this.reloj = reloj;
    }

    @Override
    public TokenEmitidoDto renovar(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw noAutenticado();
        }
        String hash = hashTokens.sha256Hex(refreshToken);
        Instant ahora = reloj.ahora();

        Optional<SesionRefreshInfo> encontrada = sesiones.buscarPorHash(hash);
        if (encontrada.isEmpty()) {
            throw noAutenticado();
        }
        SesionRefreshInfo sesion = encontrada.get();

        if (sesion.estaRevocada()) {
            // Reutilizacion de un token ya rotado: se asume posible robo.
            sesiones.revocarTodasDelUsuario(sesion.idUsuario(), ahora);
            throw noAutenticado();
        }
        if (sesion.estaExpirada(ahora)) {
            throw noAutenticado();
        }

        Usuario usuario = usuarios.buscarPorId(sesion.idUsuario())
                .orElseThrow(this::noAutenticado);
        if (!usuario.estaActivo()) {
            sesiones.revocarTodasDelUsuario(usuario.id(), ahora);
            throw new DomainException(ErrorCodes.SIN_PERMISO, "La cuenta no esta activa.");
        }

        sesiones.revocar(hash, ahora);
        return emisorTokens.emitir(usuario);
    }

    private DomainException noAutenticado() {
        return new DomainException(ErrorCodes.NO_AUTENTICADO, "Sesion invalida o expirada.");
    }
}
