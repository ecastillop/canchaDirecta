package pe.com.canchadirecta.engagement.application.port.in;

import pe.com.canchadirecta.engagement.domain.model.Calificacion;

/**
 * Calificacion de una reserva ya finalizada por parte de su titular (CU03.8, RN-04).
 */
public interface CalificarReservaUseCase {

    /**
     * Registra la calificacion de una reserva del titular autenticado.
     *
     * @param idReserva  reserva a calificar
     * @param idUsuario  titular autenticado (debe ser el dueno de la reserva)
     * @param puntaje    puntaje de 1 a 5
     * @param comentario comentario opcional (hasta 500 caracteres)
     * @return la calificacion registrada
     */
    Calificacion calificar(Long idReserva, Long idUsuario, int puntaje, String comentario);
}
