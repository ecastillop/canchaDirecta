package pe.com.canchadirecta.payments.application.port.out;

import java.math.BigDecimal;

/**
 * Puerto de salida hacia la pasarela de pago. La pasarela real esta deshabilitada
 * (REQUISITOS §2): la implementacion activa es un simulador local **determinista**, de modo
 * que las pruebas no dependen de un servicio externo.
 */
public interface PaymentGatewayPort {

    /**
     * Cobra el importe. El simulador es determinista: mismo cuerpo -> mismo resultado.
     *
     * @param codigoOperacion pista opcional del solicitante; si empieza por `RECHAZA`, el
     *                        simulador devuelve un cobro rechazado
     */
    ResultadoCobro cobrar(BigDecimal monto, String medioPago, String codigoOperacion);

    /** Resultado de un cobro simulado: si se aprobo y el codigo de operacion asignado. */
    record ResultadoCobro(boolean aprobado, String codigoOperacion) {
    }
}
