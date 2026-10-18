package pe.com.canchadirecta.shared.domain;

/** Codigos de error de negocio estables expuestos por la API. */
public final class ErrorCodes {

    public static final String DATOS_INVALIDOS = "VALIDATION_ERROR";
    public static final String CORREO_YA_REGISTRADO = "EMAIL_ALREADY_REGISTERED";
    public static final String CREDENCIALES_INVALIDAS = "INVALID_CREDENTIALS";
    public static final String NO_AUTENTICADO = "UNAUTHENTICATED";
    public static final String SIN_PERMISO = "FORBIDDEN";
    public static final String NO_ENCONTRADO = "NOT_FOUND";
    public static final String CONFLICTO = "CONFLICT";
    public static final String CUENTA_NO_VERIFICADA = "EMAIL_NOT_VERIFIED";
    /** S-4: demasiados intentos fallidos de login (HTTP 429). */
    public static final String DEMASIADOS_INTENTOS = "TOO_MANY_ATTEMPTS";
    public static final String FRANJA_NO_DISPONIBLE = "SLOT_UNAVAILABLE";
    /** Conflicto de concurrencia transitorio (deadlock/timeout de bloqueo). */
    public static final String RECURSO_BLOQUEADO = "RESOURCE_BUSY";

    private ErrorCodes() {
    }
}
