package pe.com.canchadirecta.shared.application.port.out;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Consulta de promociones vigentes para una cancha, usada al crear la reserva. Puerto compartido
 * para que `bookings` no dependa del modulo que administra el catalogo.
 */
public interface PromocionesCancha {

    /**
     * Promocion vigente para la cancha y usuario en la fecha indicada (fecha de negocio en
     * America/Lima). Devuelve vacio si esta inactiva, fuera de fechas, no aplica a la cancha o
     * el usuario ya alcanzo sus limites de canje.
     */
    Optional<Promocion> vigente(String codigo, Long idCancha, LocalDate fecha, Long idUsuario);

    /**
     * Comprueba y consume un uso del cupon con la reserva ya insertada. Bloquea la promocion y
     * las reservas asociadas para que dos canjes simultaneos no superen los limites. Devuelve
     * {@code false} si ya no quedan usos (la transaccion de la reserva debe abortarse).
     */
    boolean consumir(Long idPromocion, Long idUsuario);

    record Promocion(Long id, String codigo, String tipoDescuento, BigDecimal valor) {
    }
}
