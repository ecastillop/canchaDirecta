package pe.com.canchadirecta.payments.adapter.out.gateway;

import jakarta.enterprise.context.ApplicationScoped;
import pe.com.canchadirecta.payments.application.port.out.PaymentGatewayPort;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * Pasarela de pago simulada y determinista (REQUISITOS §2/R7: pasarela real deshabilitada).
 *
 * <p>Reglas, sin estado y sin aleatoriedad (mismo cuerpo -> mismo resultado):</p>
 * <ul>
 *   <li>Si {@code codigoOperacion} empieza por {@code RECHAZA} (sin distinguir mayusculas),
 *       el cobro se rechaza. Es la via explicita para probar el camino de pago rechazado.</li>
 *   <li>En cualquier otro caso el cobro se aprueba y se conserva el codigo recibido o, si no
 *       hay, se deriva uno reproducible a partir del importe.</li>
 * </ul>
 *
 * <p>Para conectar una pasarela real basta con reemplazar esta implementacion del puerto
 * {@link PaymentGatewayPort}.</p>
 */
@ApplicationScoped
public class SimuladorPagoAdapter implements PaymentGatewayPort {

    private static final String PREFIJO_RECHAZO = "RECHAZA";

    @Override
    public ResultadoCobro cobrar(BigDecimal monto, String medioPago, String codigoOperacion) {
        String codigo = codigoOperacion == null ? "" : codigoOperacion.trim();
        boolean rechazar = codigo.toUpperCase(Locale.ROOT).startsWith(PREFIJO_RECHAZO);
        return new ResultadoCobro(!rechazar, codigo.isEmpty() ? codigoDerivado(monto) : codigo);
    }

    /** Codigo reproducible: `SIM-` + importe sin el separador decimal (p. ej. 90.00 -> SIM-9000). */
    private static String codigoDerivado(BigDecimal monto) {
        return "SIM-" + monto.toPlainString().replace(".", "");
    }
}
