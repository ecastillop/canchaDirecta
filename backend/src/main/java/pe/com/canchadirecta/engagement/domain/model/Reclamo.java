package pe.com.canchadirecta.engagement.domain.model;

import pe.com.canchadirecta.shared.domain.DomainException;
import pe.com.canchadirecta.shared.domain.ErrorCodes;

import java.time.Instant;

/**
 * Reclamo o consulta de soporte de un usuario (CU06.6), opcionalmente asociado a una de sus
 * reservas.
 *
 * <p>El ciclo es `ABIERTO -> EN_REVISION -> RESUELTO -> CERRADO` (CHECK del esquema V1). El
 * usuario solo crea reclamos y aporta seguimientos; el cambio de estado lo hara la
 * administracion en un slice posterior.</p>
 */
public record Reclamo(
        Long id,
        Long idUsuario,
        Long idReserva,
        String asunto,
        String descripcion,
        Estado estado,
        Instant fechaRegistro
) {

    public static final int ASUNTO_MAX = 150;
    public static final int DESCRIPCION_MAX = 800;

    /** Estados admitidos por `ck_reclamo_estado` (V1). */
    public enum Estado {
        ABIERTO, EN_REVISION, RESUELTO, CERRADO
    }

    public Reclamo {
        if (idUsuario == null) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "El usuario es obligatorio.");
        }
        asunto = obligatorio(asunto, "El asunto", ASUNTO_MAX);
        descripcion = obligatorio(descripcion, "La descripcion", DESCRIPCION_MAX);
        if (estado == null) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "El estado del reclamo es obligatorio.");
        }
        if (fechaRegistro == null) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "La fecha de registro es obligatoria.");
        }
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
