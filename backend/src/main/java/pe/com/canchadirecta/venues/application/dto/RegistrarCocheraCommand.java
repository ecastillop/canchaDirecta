package pe.com.canchadirecta.venues.application.dto;

import java.math.BigDecimal;

/** Datos para registrar una cochera en un establecimiento propio (RN-08). */
public record RegistrarCocheraCommand(
        Long idEstablecimiento,
        String nombre,
        int capacidad,
        BigDecimal tarifaHora
) {
}
