package pe.com.canchadirecta.reception.adapter.in.rest.dto;

/** Vinculacion de un usuario a una sede como operador. */
public record AsignarOperadorRequest(Long idUsuario, String cargo) {
}
