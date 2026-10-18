package pe.com.canchadirecta.engagement.application.port.in;

import pe.com.canchadirecta.engagement.application.dto.ResumenCalificaciones;

/**
 * Consulta publica de las calificaciones de una cancha (CU02.5 "ver calificaciones").
 */
public interface ConsultarCalificacionesUseCase {

    /**
     * Resumen de calificaciones de una cancha: total, promedio y las mas recientes.
     *
     * @param idCancha cancha a consultar
     * @param limite   maximo de calificaciones a listar (las mas recientes primero)
     */
    ResumenCalificaciones deCancha(Long idCancha, int limite);
}
