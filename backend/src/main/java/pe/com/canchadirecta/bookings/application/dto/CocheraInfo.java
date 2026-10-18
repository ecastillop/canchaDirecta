package pe.com.canchadirecta.bookings.application.dto;

import java.math.BigDecimal;

/**
 * Vista minima de una cochera para el modulo de reservas (RN-08). `bloquearCochera` la
 * obtiene con `SELECT ... FOR UPDATE` —en el mismo orden de locking que la cancha— para
 * serializar la comprobacion de cupo y el INSERT de `reserva_cochera`.
 */
public record CocheraInfo(
        Long id,
        Long idEstablecimiento,
        String nombre,
        int capacidad,
        BigDecimal tarifaHora,
        String estado
) {

    public boolean activa() {
        return "ACTIVA".equals(estado);
    }
}
