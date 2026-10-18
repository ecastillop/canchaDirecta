package pe.com.canchadirecta.venues.domain.model;

import pe.com.canchadirecta.shared.domain.DomainException;
import pe.com.canchadirecta.shared.domain.ErrorCodes;

import java.math.BigDecimal;

/**
 * Cochera de un establecimiento (`cochera`, RN-08). Se reserva de forma **opcional**
 * junto con una reserva de cancha; `capacidad` es el numero de cupos simultaneos que el
 * establecimiento puede atender en una misma franja y `tarifaHora` el importe por hora.
 */
public record Cochera(
        Long id,
        Long idEstablecimiento,
        String nombre,
        int capacidad,
        BigDecimal tarifaHora,
        Estado estado
) {

    public enum Estado {
        ACTIVA,
        INACTIVA
    }

    public static final int NOMBRE_MAXIMO = 100;

    public Cochera {
        if (idEstablecimiento == null) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "El establecimiento es obligatorio.");
        }
        if (nombre == null || nombre.isBlank()) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "El nombre de la cochera es obligatorio.");
        }
        if (nombre.length() > NOMBRE_MAXIMO) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS,
                    "El nombre de la cochera no puede superar los " + NOMBRE_MAXIMO + " caracteres.");
        }
        if (capacidad <= 0) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "La capacidad debe ser mayor que cero.");
        }
        if (tarifaHora == null || tarifaHora.signum() < 0) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "La tarifa por hora no puede ser negativa.");
        }
        if (estado == null) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "El estado de la cochera es obligatorio.");
        }
    }

    public boolean activa() {
        return estado == Estado.ACTIVA;
    }
}
