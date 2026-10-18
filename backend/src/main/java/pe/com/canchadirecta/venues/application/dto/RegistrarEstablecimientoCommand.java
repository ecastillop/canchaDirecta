package pe.com.canchadirecta.venues.application.dto;

/** Datos para registrar un establecimiento (el propietario se resuelve del usuario). */
public record RegistrarEstablecimientoCommand(
        String documentoIdentidad,
        Long idDistrito,
        String nombre,
        String direccion,
        String referencia
) {
}
