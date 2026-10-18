package pe.com.canchadirecta.identity.domain.model;

import pe.com.canchadirecta.shared.domain.DomainException;
import pe.com.canchadirecta.shared.domain.ErrorCodes;

/**
 * Value object: hash de contrasena ya calculado. Nunca contiene la contrasena en claro.
 */
public record PasswordHash(String valor) {

    public PasswordHash {
        if (valor == null || valor.isBlank() || valor.length() > 255) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "Hash de contrasena invalido.");
        }
    }
}
