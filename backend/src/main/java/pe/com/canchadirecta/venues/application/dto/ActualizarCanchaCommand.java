package pe.com.canchadirecta.venues.application.dto;

import java.math.BigDecimal;

/** Datos editables de una cancha (CU04.1). El estado se gestiona con la baja logica. */
public record ActualizarCanchaCommand(
        String nombre,
        String descripcion,
        int capacidad,
        BigDecimal precioBase
) {
}
