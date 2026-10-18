package pe.com.canchadirecta.activities.adapter.in.rest.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pe.com.canchadirecta.activities.application.dto.ActividadCommand;
import pe.com.canchadirecta.activities.domain.model.Actividad;

import java.math.BigDecimal;
import java.time.Instant;

/** Alta o edicion de una actividad de un establecimiento. */
public record ActividadRequest(
        @NotBlank(message = "El nombre es obligatorio.")
        @Size(max = Actividad.NOMBRE_MAX, message = "El nombre no puede superar los 150 caracteres.")
        String nombre,
        @Size(max = Actividad.DESCRIPCION_MAX, message = "La descripcion no puede superar los 800 caracteres.")
        String descripcion,
        @NotNull(message = "La fecha de inicio es obligatoria.")
        Instant inicio,
        @NotNull(message = "La fecha de fin es obligatoria.")
        Instant fin,
        @Min(value = 1, message = "Los cupos deben ser al menos 1.")
        @Max(value = Actividad.CUPOS_MAX, message = "Los cupos no pueden superar 500.")
        int cupos,
        @NotNull(message = "El precio es obligatorio.")
        @DecimalMin(value = "0.00", message = "El precio no puede ser negativo.")
        BigDecimal precio
) {

    public ActividadCommand aCommand() {
        return new ActividadCommand(nombre, descripcion, inicio, fin, cupos, precio);
    }
}
