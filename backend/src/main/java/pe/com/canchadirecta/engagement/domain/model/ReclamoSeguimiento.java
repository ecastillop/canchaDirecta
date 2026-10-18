package pe.com.canchadirecta.engagement.domain.model;

import pe.com.canchadirecta.shared.domain.DomainException;
import pe.com.canchadirecta.shared.domain.ErrorCodes;

import java.time.Instant;

/**
 * Comentario anadido a la conversacion de un reclamo (tabla `reclamo_seguimiento`).
 */
public record ReclamoSeguimiento(
        Long id,
        Long idReclamo,
        Long idUsuario,
        String comentario,
        Instant fecha
) {

    public static final int COMENTARIO_MAX = 800;

    public ReclamoSeguimiento {
        if (idReclamo == null) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "El reclamo es obligatorio.");
        }
        if (comentario == null || comentario.trim().isEmpty()) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "El comentario es obligatorio.");
        }
        comentario = comentario.trim();
        if (comentario.length() > COMENTARIO_MAX) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS,
                    "El comentario no puede superar " + COMENTARIO_MAX + " caracteres.");
        }
        if (fecha == null) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "La fecha del seguimiento es obligatoria.");
        }
    }
}
