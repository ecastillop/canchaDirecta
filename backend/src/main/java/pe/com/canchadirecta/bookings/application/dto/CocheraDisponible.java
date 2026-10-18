package pe.com.canchadirecta.bookings.application.dto;

import java.math.BigDecimal;

/** Cupo de una cochera en una franja concreta (RN-08). */
public record CocheraDisponible(
        Long id,
        String nombre,
        int capacidad,
        int ocupadas,
        BigDecimal tarifaHora
) {

    /** Cupos libres en la franja; nunca negativo. */
    public int cupoDisponible() {
        return Math.max(0, capacidad - ocupadas);
    }

    public boolean disponible() {
        return cupoDisponible() > 0;
    }
}
