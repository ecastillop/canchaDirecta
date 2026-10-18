package pe.com.canchadirecta.shared.domain;

/**
 * Excepcion de dominio con un codigo de negocio estable (sin acoplar a HTTP).
 */
public class DomainException extends RuntimeException {

    private final String code;

    public DomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
