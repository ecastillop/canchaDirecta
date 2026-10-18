package pe.com.canchadirecta.bookings.application.dto;

import pe.com.canchadirecta.bookings.domain.model.FranjaHoraria;

/**
 * Tramo del dia que no esta disponible y su motivo: una reserva que retiene cupo o un bloqueo
 * operativo de la cancha. Es la unica informacion que la disponibilidad publica expone de una
 * franja ocupada: nunca el titular ni el motivo del bloqueo (ADR-0047).
 */
public record OcupacionFranja(
        FranjaHoraria franja,
        Motivo motivo
) {

    /** Origen de la ocupacion; el cliente solo necesita saber si podria liberarse. */
    public enum Motivo {
        RESERVADA,
        BLOQUEADA
    }
}
