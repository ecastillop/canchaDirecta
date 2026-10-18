package pe.com.canchadirecta.bookings.application.dto;

import java.math.BigDecimal;

/**
 * Vista minima de la cancha. `bloquearCancha` la obtiene con `SELECT ... FOR UPDATE`
 * (ese bloqueo de fila es el mecanismo de serializacion de reservas de la misma
 * cancha); `buscarCancha` la lee sin bloqueo para consultas de disponibilidad.
 *
 * <p>`idEstablecimiento` permite comprobar la coherencia de la cochera opcional
 * (RN-08): una cochera solo puede usarse en reservas de su mismo establecimiento.</p>
 */
public record CanchaInfo(Long id, Long idEstablecimiento, BigDecimal precioBase, String estado) {

    public boolean reservable() {
        return "ACTIVA".equals(estado);
    }
}
