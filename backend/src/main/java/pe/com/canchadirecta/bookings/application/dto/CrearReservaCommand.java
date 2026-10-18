package pe.com.canchadirecta.bookings.application.dto;

import java.time.Instant;

/** Datos para crear una reserva (el usuario procede del token autenticado). */
public record CrearReservaCommand(Long idUsuario, Long idCancha, Instant inicio, Instant fin, Long idCochera,
                                  String codigoPromocion, String claveIdempotencia) {
}
