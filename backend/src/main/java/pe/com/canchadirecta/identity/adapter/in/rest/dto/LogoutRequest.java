package pe.com.canchadirecta.identity.adapter.in.rest.dto;

/** Cuerpo de la peticion POST /api/v1/auth/logout. */
public record LogoutRequest(
        String refreshToken
) {
}
