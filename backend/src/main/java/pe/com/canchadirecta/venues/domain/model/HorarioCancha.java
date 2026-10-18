package pe.com.canchadirecta.venues.domain.model;

import pe.com.canchadirecta.shared.domain.DomainException;
import pe.com.canchadirecta.shared.domain.ErrorCodes;

import java.math.BigDecimal;
import java.time.LocalTime;

/**
 * Franja semanal de atencion de una cancha (`horario_cancha`). `diaSemana` usa la
 * numeracion ISO (1 = lunes ... 7 = domingo) y las horas son locales del establecimiento.
 * `precio` puede ser nulo: en ese caso rige el precio base de la cancha.
 */
public record HorarioCancha(
        Long id,
        Long idCancha,
        int diaSemana,
        LocalTime horaInicio,
        LocalTime horaFin,
        BigDecimal precio,
        boolean activo
) {

    public static final int DIA_MINIMO = 1;
    public static final int DIA_MAXIMO = 7;

    public HorarioCancha {
        if (idCancha == null) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "La cancha es obligatoria.");
        }
        if (diaSemana < DIA_MINIMO || diaSemana > DIA_MAXIMO) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS,
                    "El dia de la semana debe estar entre 1 (lunes) y 7 (domingo).");
        }
        if (horaInicio == null || horaFin == null) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "La franja del horario es obligatoria.");
        }
        if (!horaInicio.isBefore(horaFin)) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS,
                    "La hora de inicio del horario debe ser anterior a la de fin.");
        }
        if (precio != null && precio.signum() < 0) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS,
                    "El precio del horario no puede ser negativo.");
        }
    }

    /** Solapamiento de dos franjas del mismo dia (intervalos semiabiertos). */
    public boolean seSolapaCon(HorarioCancha otro) {
        return otro != null
                && otro.diaSemana() == diaSemana
                && horaInicio.isBefore(otro.horaFin())
                && horaFin.isAfter(otro.horaInicio());
    }
}
