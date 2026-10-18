package pe.com.canchadirecta.platform.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Vistas de supervision de solo lectura para la administracion (CU06.3/CU06.4).
 */
public final class SupervisionAdmin {

    private SupervisionAdmin() {
    }

    /** Establecimiento con su propietario, distrito y numero de canchas. */
    public record Establecimiento(
            Long id,
            String nombre,
            String distrito,
            String propietario,
            String estado,
            long canchas
    ) {
    }

    /** Reserva de cualquier usuario con su cancha y establecimiento. */
    public record Reserva(
            Long id,
            String cancha,
            String establecimiento,
            String titular,
            Instant inicio,
            Instant fin,
            BigDecimal montoCancha,
            BigDecimal montoCochera,
            String estado
    ) {
    }

    /** Pago con el total reembolsado acumulado. */
    public record Pago(
            Long id,
            Long idReserva,
            BigDecimal monto,
            String medioPago,
            String estado,
            Instant fechaPago,
            BigDecimal reembolsado
    ) {
    }
}
