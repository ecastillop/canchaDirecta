package pe.com.canchadirecta.bookings.application.dto;

import pe.com.canchadirecta.bookings.domain.model.FranjaHoraria;

import java.math.BigDecimal;

/** Franja libre de una cancha junto con el precio aplicable a esa franja. */
public record FranjaDisponible(FranjaHoraria franja, BigDecimal precio) {
}
