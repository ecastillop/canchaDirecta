package pe.com.canchadirecta.venues.application.port.in;

import pe.com.canchadirecta.venues.application.dto.RegistrarBloqueoCommand;
import pe.com.canchadirecta.venues.application.dto.RegistrarHorarioCommand;
import pe.com.canchadirecta.venues.domain.model.BloqueoCancha;
import pe.com.canchadirecta.venues.domain.model.HorarioCancha;

import java.time.Instant;
import java.util.List;

/**
 * Agenda de una cancha: horarios semanales de atencion (HU-005) y bloqueos operativos
 * (RN-03). Todas las operaciones de escritura exigen ser el propietario del
 * establecimiento al que pertenece la cancha.
 */
public interface GestionAgendaUseCase {

    /** Horarios activos de la cancha; es publico, igual que el catalogo. */
    List<HorarioCancha> listarHorarios(Long idCancha);

    Long registrarHorario(Long idUsuario, RegistrarHorarioCommand command);

    void eliminarHorario(Long idUsuario, Long idCancha, Long idHorario);

    List<BloqueoCancha> listarBloqueos(Long idUsuario, Long idCancha, Instant desde, Instant hasta);

    Long registrarBloqueo(Long idUsuario, RegistrarBloqueoCommand command);

    void eliminarBloqueo(Long idUsuario, Long idCancha, Long idBloqueo);
}
