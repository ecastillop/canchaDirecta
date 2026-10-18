package pe.com.canchadirecta.venues.domain.model;

import pe.com.canchadirecta.shared.domain.DomainException;
import pe.com.canchadirecta.shared.domain.ErrorCodes;

import java.time.Instant;

/**
 * Bloqueo operativo de una cancha (`bloqueo_cancha`, RN-03): mantenimiento, lluvia,
 * eventos. Mientras exista el bloqueo, la franja no se ofrece en la disponibilidad ni
 * puede reservarse. Los instantes son absolutos (UTC).
 */
public record BloqueoCancha(
        Long id,
        Long idCancha,
        Instant inicio,
        Instant fin,
        String motivo
) {

    public static final int MOTIVO_MAXIMO = 200;

    public BloqueoCancha {
        if (idCancha == null) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "La cancha es obligatoria.");
        }
        if (inicio == null || fin == null) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "El rango del bloqueo es obligatorio.");
        }
        if (!inicio.isBefore(fin)) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS,
                    "El inicio del bloqueo debe ser anterior al fin.");
        }
        if (motivo == null || motivo.isBlank()) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "El motivo del bloqueo es obligatorio.");
        }
        if (motivo.length() > MOTIVO_MAXIMO) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS,
                    "El motivo del bloqueo no puede superar los " + MOTIVO_MAXIMO + " caracteres.");
        }
    }
}
