package pe.com.canchadirecta.activities.domain.model;

import pe.com.canchadirecta.shared.domain.DomainException;
import pe.com.canchadirecta.shared.domain.ErrorCodes;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Actividad organizada por un establecimiento (torneo, clase o partido abierto) con
 * cupos limitados. Se publica con `ACTIVA` y el propietario puede cancelarla.
 */
public record Actividad(
        Long id,
        Long idEstablecimiento,
        String establecimiento,
        String distrito,
        String nombre,
        String descripcion,
        Instant inicio,
        Instant fin,
        int cupos,
        BigDecimal precio,
        Estado estado,
        long inscritos
) {

    public static final int NOMBRE_MAX = 150;
    public static final int DESCRIPCION_MAX = 800;
    public static final int CUPOS_MAX = 500;

    public enum Estado {
        ACTIVA, CANCELADA
    }

    public Actividad {
        if (idEstablecimiento == null) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "El establecimiento es obligatorio.");
        }
        nombre = obligatorio(nombre, "El nombre", NOMBRE_MAX);
        if (descripcion != null) {
            descripcion = descripcion.trim();
            if (descripcion.length() > DESCRIPCION_MAX) {
                throw new DomainException(ErrorCodes.DATOS_INVALIDOS,
                        "La descripcion no puede superar " + DESCRIPCION_MAX + " caracteres.");
            }
            if (descripcion.isEmpty()) {
                descripcion = null;
            }
        }
        if (inicio == null || fin == null) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS,
                    "La fecha de inicio y la de fin son obligatorias.");
        }
        if (!fin.isAfter(inicio)) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS,
                    "La fecha de fin debe ser posterior a la de inicio.");
        }
        if (cupos <= 0 || cupos > CUPOS_MAX) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS,
                    "Los cupos deben estar entre 1 y " + CUPOS_MAX + ".");
        }
        if (precio == null || precio.signum() < 0) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "El precio no puede ser negativo.");
        }
        if (estado == null) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "El estado de la actividad es obligatorio.");
        }
        if (inscritos < 0) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "Las inscripciones no pueden ser negativas.");
        }
    }

    public int cuposDisponibles() {
        return (int) Math.max(0, cupos - inscritos);
    }

    public boolean admiteInscripciones(Instant ahora) {
        return estado == Estado.ACTIVA && fin.isAfter(ahora);
    }

    private static String obligatorio(String valor, String campo, int maximo) {
        if (valor == null || valor.trim().isEmpty()) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, campo + " es obligatorio.");
        }
        String limpio = valor.trim();
        if (limpio.length() > maximo) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS,
                    campo + " no puede superar " + maximo + " caracteres.");
        }
        return limpio;
    }
}
