package pe.com.canchadirecta.reception.application.dto;

/** Cliente registrado localizado por nombre, correo o telefono (reserva presencial). */
public record ClienteRecepcion(Long id, String nombre, String correo, String telefono) {
}
