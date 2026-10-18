package pe.com.canchadirecta.bookings.domain.model;

import pe.com.canchadirecta.shared.domain.DomainException;
import pe.com.canchadirecta.shared.domain.ErrorCodes;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Reserva de una cancha. El importe (`montoCancha`) se congela al crear la reserva:
 * cambios posteriores del precio base de la cancha no alteran reservas existentes.
 *
 * <p>`cochera` es la cochera opcional (RN-08) incluida en la reserva; es `null` cuando el
 * titular no contrato cochera. Su `monto` tambien se congela al crear la reserva.</p>
 */
public record Reserva(
        Long id,
        Long idUsuario,
        Long idCancha,
        FranjaHoraria franja,
        BigDecimal montoCancha,
        BigDecimal montoDescuento,
        Estado estado,
        Instant fechaCreacion,
        CocheraReservada cochera
) {

    /** Cochera contratada con la reserva: id del recurso y monto congelado. */
    public record CocheraReservada(Long idCochera, BigDecimal monto) {

        public CocheraReservada {
            if (idCochera == null) {
                throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "La cochera es obligatoria.");
            }
            if (monto == null || monto.signum() < 0) {
                throw new DomainException(ErrorCodes.DATOS_INVALIDOS,
                        "El monto de la cochera no puede ser negativo.");
            }
        }
    }

    public enum Estado {
        PENDIENTE,
        CONFIRMADA,
        CANCELADA,
        RECHAZADA,
        /** Solicitud PENDIENTE sin pago aprobado que vencio su hold de pago (ADR-0022). */
        EXPIRADA,
        FINALIZADA
    }

    public Reserva {
        if (idUsuario == null || idCancha == null) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS,
                    "El usuario y la cancha son obligatorios en una reserva.");
        }
        if (franja == null) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "La franja horaria es obligatoria.");
        }
        if (montoCancha == null || montoCancha.signum() < 0) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "El monto de la cancha no puede ser negativo.");
        }
        if (estado == null) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "El estado de la reserva es obligatorio.");
        }
    }

    /** Estados que ocupan la franja y por tanto impiden otra reserva solapada. */
    public static boolean ocupaFranja(Estado estado) {
        return estado == Estado.PENDIENTE || estado == Estado.CONFIRMADA;
    }
}
