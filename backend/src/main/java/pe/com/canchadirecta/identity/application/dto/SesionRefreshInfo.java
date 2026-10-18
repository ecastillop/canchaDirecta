package pe.com.canchadirecta.identity.application.dto;

import java.time.Instant;

/** Vista de una sesion de refresco persistida (sin el token en claro). */
public record SesionRefreshInfo(
        Long idSesion,
        Long idUsuario,
        Instant expiraEn,
        Instant revocadoEn
) {
    public boolean estaRevocada() {
        return revocadoEn != null;
    }

    public boolean estaExpirada(Instant ahora) {
        return !expiraEn.isAfter(ahora);
    }
}
