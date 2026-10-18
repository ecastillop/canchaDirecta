package pe.com.canchadirecta.identity.application.port.out;

import pe.com.canchadirecta.identity.application.dto.SesionRefreshInfo;

import java.time.Instant;
import java.util.Optional;

/** Puerto de salida: sesiones de refresco (solo se persiste el hash del token). */
public interface SesionRefreshRepositorio {

    void registrarSesion(Long idUsuario, String tokenHash, Instant emitidoEn, Instant expiraEn);

    Optional<SesionRefreshInfo> buscarPorHash(String tokenHash);

    /** Marca una sesion como revocada (idempotente). */
    void revocar(String tokenHash, Instant revocadoEn);

    /** Revoca todas las sesiones de un usuario (deteccion de reutilizacion / logout global). */
    void revocarTodasDelUsuario(Long idUsuario, Instant revocadoEn);
}
