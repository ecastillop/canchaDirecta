package pe.com.canchadirecta.venues.domain.model;

import pe.com.canchadirecta.shared.domain.DomainException;
import pe.com.canchadirecta.shared.domain.ErrorCodes;

import java.math.BigDecimal;

/**
 * Cancha (unidad reservable). El precio base es el importe de referencia por hora;
 * el importe final de cada reserva se calcula y se congela al crearla.
 */
public record Cancha(
        Long id,
        Long idEstablecimiento,
        String nombre,
        int capacidad,
        BigDecimal precioBase,
        Estado estado
) {

    public enum Estado {
        ACTIVA,
        INACTIVA,
        MANTENIMIENTO
    }

    public Cancha {
        if (nombre == null || nombre.isBlank()) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "El nombre de la cancha es obligatorio.");
        }
        if (capacidad <= 0) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "La capacidad debe ser mayor que cero.");
        }
        if (precioBase == null || precioBase.signum() < 0) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "El precio base no puede ser negativo.");
        }
        if (estado == null) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "El estado de la cancha es obligatorio.");
        }
    }

    public boolean esReservable() {
        return estado == Estado.ACTIVA;
    }
}
