package pe.com.canchadirecta.bookings.application.port.in;

import pe.com.canchadirecta.bookings.application.dto.CocheraDisponible;

import java.time.Instant;
import java.util.List;

/**
 * Consulta del cupo de cocheras de una cancha (RN-08): devuelve las cocheras del
 * establecimiento de la cancha con los cupos libres en una franja concreta.
 */
public interface ConsultarCocherasUseCase {

    List<CocheraDisponible> consultar(Long idCancha, Instant inicio, Instant fin);
}
