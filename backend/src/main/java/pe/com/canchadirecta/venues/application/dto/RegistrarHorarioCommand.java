package pe.com.canchadirecta.venues.application.dto;

import java.math.BigDecimal;
import java.time.LocalTime;

/** Datos para registrar una franja semanal de atencion (HU-005). */
public record RegistrarHorarioCommand(
        Long idCancha,
        int diaSemana,
        LocalTime inicio,
        LocalTime fin,
        BigDecimal precio
) {
}
