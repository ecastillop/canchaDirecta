package pe.com.canchadirecta.engagement.domain.model;

import pe.com.canchadirecta.shared.domain.DomainException;
import pe.com.canchadirecta.shared.domain.ErrorCodes;

import java.time.Instant;

/**
 * Calificacion (1 a 5) de una reserva ya finalizada, con comentario opcional.
 *
 * <p><b>RN-04:</b> una reserva admite una sola calificacion, garantizada por
 * `UNIQUE(id_reserva)` en el esquema. La calificacion cuelga de la **reserva** (no de la
 * cancha) para poder verificar quien califica (el titular) y que la reserva ya se uso.</p>
 */
public record Calificacion(
        Long id,
        Long idReserva,
        int puntaje,
        String comentario,
        Instant fechaRegistro
) {

    public static final int PUNTAJE_MIN = 1;
    public static final int PUNTAJE_MAX = 5;
    public static final int COMENTARIO_MAX = 500;

    public Calificacion {
        if (idReserva == null) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "La reserva es obligatoria.");
        }
        if (puntaje < PUNTAJE_MIN || puntaje > PUNTAJE_MAX) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS,
                    "El puntaje debe estar entre " + PUNTAJE_MIN + " y " + PUNTAJE_MAX + ".");
        }
        comentario = normalizar(comentario);
        if (fechaRegistro == null) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "La fecha de registro es obligatoria.");
        }
    }

    /** Recorta espacios, convierte el vacio en `null` y rechaza comentarios demasiado largos. */
    private static String normalizar(String comentario) {
        if (comentario == null) {
            return null;
        }
        String limpio = comentario.trim();
        if (limpio.isEmpty()) {
            return null;
        }
        if (limpio.length() > COMENTARIO_MAX) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS,
                    "El comentario no puede superar " + COMENTARIO_MAX + " caracteres.");
        }
        return limpio;
    }
}
