package pe.com.canchadirecta.reception.application.dto;

/** Operador vinculado a una sede (personal y permisos). */
public record OperadorAsignado(Long idUsuario, String nombre, String correo, String cargo, boolean activo) {
}
