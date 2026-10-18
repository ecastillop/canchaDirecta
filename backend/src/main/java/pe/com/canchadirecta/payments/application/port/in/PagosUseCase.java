package pe.com.canchadirecta.payments.application.port.in;

import pe.com.canchadirecta.payments.application.dto.DetallePago;
import pe.com.canchadirecta.payments.domain.model.Pago;

/**
 * Casos de uso del pago simulado de una reserva (CU03.4 / CU05.1).
 */
public interface PagosUseCase {

    /**
     * Procesa el pago simulado de una reserva propia. Devuelve el pago resultante
     * (`APROBADO` o `RECHAZADO`) y registra su evento.
     */
    Pago pagar(Long idUsuario, Long idReserva, String medioPago, String codigoOperacion);

    /** Ultimo pago de la reserva con sus reembolsos; 404 si la reserva aun no tiene pago. */
    DetallePago consultar(Long idUsuario, Long idReserva);
}
