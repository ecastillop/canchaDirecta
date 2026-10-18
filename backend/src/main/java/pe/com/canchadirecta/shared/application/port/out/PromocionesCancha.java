package pe.com.canchadirecta.shared.application.port.out;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Consulta de promociones vigentes para una cancha, usada al crear la reserva. Puerto compartido
 * para que `bookings` no dependa del modulo que administra el catalogo.
 */
public interface PromocionesCancha {

    /** Promocion vigente para la cancha en la fecha indicada (fecha de negocio en America/Lima). */
    Optional<Promocion> vigente(String codigo, Long idCancha, LocalDate fecha);

    record Promocion(String codigo, String tipoDescuento, BigDecimal valor) {
    }
}
