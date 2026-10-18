package pe.com.canchadirecta.payments.adapter.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cuerpo de `POST /api/v1/reservas/{idReserva}/pago`. */
public record PagoRequest(
        @NotBlank @Size(max = 30) String medioPago,
        @Size(max = 80) String codigoOperacion
) {
}
