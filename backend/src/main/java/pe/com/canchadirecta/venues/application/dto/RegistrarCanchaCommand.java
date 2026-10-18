package pe.com.canchadirecta.venues.application.dto;

import java.math.BigDecimal;

/** Datos para registrar una cancha dentro de un establecimiento. */
public record RegistrarCanchaCommand(
        Long idEstablecimiento,
        String nombre,
        String descripcion,
        int capacidad,
        BigDecimal precioBase
) {
}
