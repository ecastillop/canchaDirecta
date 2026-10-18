package pe.com.canchadirecta.reception.application.dto;

/** Sede a la que el operador (u owner/admin) tiene acceso en el portal de recepcion. */
public record EstablecimientoRecepcion(Long id, String nombre, String distrito) {
}
