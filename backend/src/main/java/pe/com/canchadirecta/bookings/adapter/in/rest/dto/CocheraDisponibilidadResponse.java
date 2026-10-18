package pe.com.canchadirecta.bookings.adapter.in.rest.dto;

import java.math.BigDecimal;

/** Cupo de una cochera en la franja consultada (RN-08). */
public record CocheraDisponibilidadResponse(
        Long id,
        String nombre,
        int capacidad,
        int cupoDisponible,
        BigDecimal tarifaHora
) {
}
