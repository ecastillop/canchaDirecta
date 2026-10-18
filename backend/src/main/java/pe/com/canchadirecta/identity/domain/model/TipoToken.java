package pe.com.canchadirecta.identity.domain.model;

/** Tipos de token de un solo uso. Deben coincidir con la restriccion CHECK de `auth_token`. */
public enum TipoToken {
    EMAIL_VERIFY,
    PASSWORD_RESET
}
