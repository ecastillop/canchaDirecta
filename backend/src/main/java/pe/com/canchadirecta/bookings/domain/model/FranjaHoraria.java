package pe.com.canchadirecta.bookings.domain.model;

import pe.com.canchadirecta.shared.domain.DomainException;
import pe.com.canchadirecta.shared.domain.ErrorCodes;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Franja horaria de una reserva. Se define como un intervalo semiabierto
 * [inicio, fin): una reserva de 18:00-19:00 y otra de 19:00-20:00 NO se solapan.
 */
public record FranjaHoraria(Instant inicio, Instant fin) {

    public static final long MINUTOS_MINIMOS = 30;
    public static final long MINUTOS_MAXIMOS = 180;

    public FranjaHoraria {
        if (inicio == null || fin == null) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "La franja horaria es obligatoria.");
        }
        if (!inicio.isBefore(fin)) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "El inicio debe ser anterior al fin.");
        }
        long minutos = Duration.between(inicio, fin).toMinutes();
        if (minutos < MINUTOS_MINIMOS) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS,
                    "La reserva debe durar al menos " + MINUTOS_MINIMOS + " minutos.");
        }
        if (minutos > MINUTOS_MAXIMOS) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS,
                    "La reserva no puede durar mas de " + MINUTOS_MAXIMOS + " minutos.");
        }
    }

    public long minutos() {
        return Duration.between(inicio, fin).toMinutes();
    }

    /**
     * Solapamiento de intervalos semiabiertos: hay choque si cada uno empieza antes
     * de que termine el otro.
     */
    public boolean seSolapaCon(Instant otroInicio, Instant otroFin) {
        return inicio.isBefore(otroFin) && fin.isAfter(otroInicio);
    }

    /**
     * Divide una ventana de atencion `[inicioVentana, finVentana)` en bloques
     * consecutivos de `minutos` alineados a su inicio, descartando los que se solapen
     * con alguna franja ocupada. La alineacion es fija respecto a la ventana, de modo
     * que el catalogo de bloques ofrecidos no cambia cuando se reserva uno de ellos.
     *
     * <p>La ventana puede ser mucho mas larga que una reserva: la duracion limitada a
     * 30-180 minutos aplica a los bloques resultantes, no a la ventana.</p>
     *
     * @param ocupadas reservas vigentes y bloqueos que intersectan la ventana
     * @param minutos  duracion de cada bloque reservable (30-180)
     */
    public static List<FranjaHoraria> bloquesLibres(Instant inicioVentana, Instant finVentana,
                                                    List<FranjaHoraria> ocupadas, long minutos) {
        if (minutos < MINUTOS_MINIMOS || minutos > MINUTOS_MAXIMOS) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS,
                    "La duracion debe estar entre " + MINUTOS_MINIMOS + " y " + MINUTOS_MAXIMOS + " minutos.");
        }
        List<FranjaHoraria> libres = new ArrayList<>();
        for (Instant cursor = inicioVentana; ; cursor = cursor.plus(minutos, ChronoUnit.MINUTES)) {
            Instant posibleFin = cursor.plus(minutos, ChronoUnit.MINUTES);
            if (posibleFin.isAfter(finVentana)) {
                break;
            }
            boolean ocupado = false;
            for (FranjaHoraria franjaOcupada : ocupadas) {
                if (franjaOcupada.seSolapaCon(cursor, posibleFin)) {
                    ocupado = true;
                    break;
                }
            }
            if (!ocupado) {
                libres.add(new FranjaHoraria(cursor, posibleFin));
            }
        }
        return libres;
    }
}
