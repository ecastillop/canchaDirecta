package pe.com.canchadirecta.reception.application.dto;

/** Cancha disponible para la reserva presencial en las sedes accesibles. */
public record CanchaRecepcion(Long id, Long idEstablecimiento, String nombre) {
}
