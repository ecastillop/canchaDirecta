package pe.com.canchadirecta.venues.domain.model;

import pe.com.canchadirecta.shared.domain.DomainException;
import pe.com.canchadirecta.shared.domain.ErrorCodes;

/** Establecimiento deportivo: agrupa canchas y pertenece a un propietario. */
public record Establecimiento(
        Long id,
        Long idPropietario,
        Long idDistrito,
        String nombre,
        String direccion,
        String referencia,
        Estado estado
) {

    public enum Estado {
        ACTIVO,
        INACTIVO
    }

    public Establecimiento {
        if (idPropietario == null) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "El propietario es obligatorio.");
        }
        if (idDistrito == null) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "El distrito es obligatorio.");
        }
        if (nombre == null || nombre.isBlank()) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "El nombre del establecimiento es obligatorio.");
        }
        if (direccion == null || direccion.isBlank()) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "La direccion es obligatoria.");
        }
        if (estado == null) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "El estado del establecimiento es obligatorio.");
        }
    }
}
