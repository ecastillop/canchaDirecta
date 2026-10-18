package pe.com.canchadirecta.platform.adapter.in.rest.dto;

import pe.com.canchadirecta.platform.domain.model.SupervisionAdmin;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Vistas de supervision de solo lectura (CU06.3/CU06.4).
 */
public final class AdminSupervisionResponse {

    private AdminSupervisionResponse() {
    }

    public record Establecimiento(
            Long id,
            String nombre,
            String distrito,
            String propietario,
            String estado,
            long canchas
    ) {

        public static Establecimiento de(SupervisionAdmin.Establecimiento item) {
            return new Establecimiento(item.id(), item.nombre(), item.distrito(),
                    item.propietario(), item.estado(), item.canchas());
        }
    }

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

        public static Reserva de(SupervisionAdmin.Reserva item) {
            return new Reserva(item.id(), item.cancha(), item.establecimiento(), item.titular(),
                    item.inicio(), item.fin(), item.montoCancha(), item.montoCochera(), item.estado());
        }
    }

    public record Pago(
            Long id,
            Long idReserva,
            BigDecimal monto,
            String medioPago,
            String estado,
            Instant fechaPago,
            BigDecimal reembolsado
    ) {

        public static Pago de(SupervisionAdmin.Pago item) {
            return new Pago(item.id(), item.idReserva(), item.monto(), item.medioPago(),
                    item.estado(), item.fechaPago(), item.reembolsado());
        }
    }
}
