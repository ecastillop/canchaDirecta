package pe.com.canchadirecta.platform.adapter.in.rest.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pe.com.canchadirecta.platform.domain.model.PromocionAdmin;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Alta de una promocion por el propietario; se asocia a todas las canchas del establecimiento.
 */
public record CrearPromocionPropietarioRequest(
        @NotBlank(message = "El codigo es obligatorio.")
        @Size(max = PromocionAdmin.CODIGO_MAX, message = "El codigo no puede superar 30 caracteres.")
        String codigo,
        @NotBlank(message = "La descripcion es obligatoria.")
        @Size(max = PromocionAdmin.DESCRIPCION_MAX,
                message = "La descripcion no puede superar 200 caracteres.")
        String descripcion,
        @NotBlank(message = "El tipo de descuento es obligatorio.")
        String tipoDescuento,
        @NotNull(message = "El valor es obligatorio.")
        @DecimalMin(value = "0.01", message = "El valor debe ser mayor que cero.")
        BigDecimal valor,
        @NotNull(message = "La fecha de inicio es obligatoria.")
        LocalDate fechaInicio,
        @NotNull(message = "La fecha de fin es obligatoria.")
        LocalDate fechaFin
) {
}
