package pe.com.canchadirecta.identity.application.dto;

import java.time.Instant;

/** Vista de un token de un solo uso (nunca se expone el valor en claro). */
public record TokenAuthInfo(
        Long idToken,
        Long idUsuario,
        Instant expiraEn,
        Instant usadoEn
) {
    public boolean estaUsado() {
        return usadoEn != null;
    }

    public boolean estaExpirado(Instant ahora) {
        return !expiraEn.isAfter(ahora);
    }
}
