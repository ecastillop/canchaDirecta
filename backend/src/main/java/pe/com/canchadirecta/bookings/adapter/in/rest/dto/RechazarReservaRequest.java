package pe.com.canchadirecta.bookings.adapter.in.rest.dto;

import jakarta.validation.constraints.Size;

/**
 * Motivo opcional del rechazo. Se guarda en `historial_reserva.observacion` precedido
 * del texto fijo "Rechazada por el propietario" (29 caracteres), por lo que se limita a
 * 200 para no exceder el VARCHAR(250) de la columna.
 */
public record RechazarReservaRequest(@Size(max = 200, message = "El motivo no puede superar los 200 caracteres.") String motivo) {
}
