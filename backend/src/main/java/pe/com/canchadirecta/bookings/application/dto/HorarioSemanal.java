package pe.com.canchadirecta.bookings.application.dto;

import java.math.BigDecimal;
import java.time.LocalTime;

/**
 * Ventana de atencion semanal de una cancha (`horario_cancha`), en hora local del
 * establecimiento. `diaSemana` usa la numeracion ISO (1 = lunes ... 7 = domingo) y
 * `precio` puede ser nulo: en ese caso se usa el precio base de la cancha.
 */
public record HorarioSemanal(int diaSemana, LocalTime inicio, LocalTime fin, BigDecimal precio) {
}
