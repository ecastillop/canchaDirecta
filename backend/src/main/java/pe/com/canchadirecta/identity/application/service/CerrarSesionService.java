package pe.com.canchadirecta.identity.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import pe.com.canchadirecta.identity.application.port.in.CerrarSesionUseCase;
import pe.com.canchadirecta.identity.application.port.out.HashTokens;
import pe.com.canchadirecta.shared.application.port.out.Reloj;
import pe.com.canchadirecta.identity.application.port.out.SesionRefreshRepositorio;

/**
 * Cierre de sesion por revocacion del refresco. Es idempotente y no revela si el
 * token existia.
 */
@ApplicationScoped
public class CerrarSesionService implements CerrarSesionUseCase {

    private final SesionRefreshRepositorio sesiones;
    private final HashTokens hashTokens;
    private final Reloj reloj;

    @Inject
    public CerrarSesionService(SesionRefreshRepositorio sesiones, HashTokens hashTokens, Reloj reloj) {
        this.sesiones = sesiones;
        this.hashTokens = hashTokens;
        this.reloj = reloj;
    }

    @Override
    public void cerrar(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        sesiones.revocar(hashTokens.sha256Hex(refreshToken), reloj.ahora());
    }
}
