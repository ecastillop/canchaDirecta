package pe.com.canchadirecta.venues.application.dto;

/** Datos editables de un establecimiento (CU04.1). */
public record ActualizarEstablecimientoCommand(
        Long idDistrito,
        String nombre,
        String direccion,
        String referencia
) {
}
