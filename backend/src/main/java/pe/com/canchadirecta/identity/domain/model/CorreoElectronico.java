package pe.com.canchadirecta.identity.domain.model;

import pe.com.canchadirecta.shared.domain.DomainException;
import pe.com.canchadirecta.shared.domain.ErrorCodes;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Value object: correo electronico normalizado (minusculas, sin espacios).
 * La unicidad case-insensitive la garantiza la colacion de MySQL.
 */
public record CorreoElectronico(String valor) {

    private static final Pattern FORMATO =
            Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public CorreoElectronico {
        if (valor == null || valor.isBlank()) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "El correo es obligatorio.");
        }
        String normalizado = valor.trim().toLowerCase(Locale.ROOT);
        if (normalizado.length() > 150 || !FORMATO.matcher(normalizado).matches()) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "El correo no tiene un formato valido.");
        }
        valor = normalizado;
    }
}
