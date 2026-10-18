package pe.com.canchadirecta.bookings.application.service;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import pe.com.canchadirecta.bookings.application.port.in.FinalizarReservasUseCase;
import pe.com.canchadirecta.bookings.application.port.out.ReservaRepositorio;
import pe.com.canchadirecta.bookings.domain.model.Reserva;
import pe.com.canchadirecta.shared.application.port.out.AvisosUsuario;

import java.util.List;

/**
 * Job de cierre automatico de reservas confirmadas ya concluidas (L-15).
 *
 * <p>Una reserva `CONFIRMADA` cuyo bloque (`fin`) quedo en el pasado pasa a `FINALIZADA`,
 * sin cambios de importe ni reembolso y con la traza en `historial_reserva`. El adaptador
 * toma el bloqueo exclusivo de las filas afectadas (`FOR UPDATE`) para no cerrar una reserva
 * mientras el propietario la revoca. Corrige ademas la deriva de estado (`CONFIRMADA` que
 * ya paso) que dejaba el cierre contable del propietario (RN-07) sin frontera temporal.</p>
 *
 * <p>El disparador periodico solo delega en {@link #finalizarConcluidas(int)}: las pruebas
 * invocan el caso de uso de forma directa y determinista (el temporizador esta desactivado
 * en el perfil `test`).</p>
 */
@ApplicationScoped
public class FinalizarReservasService implements FinalizarReservasUseCase {

    /** Observacion con la que queda registrado el cierre en `historial_reserva`. */
    private static final String MOTIVO = "Finalizada: la franja ya concluyo";

    /** Intervalo del job. Fijo y corto: el cierre se apoya en el reloj de la base (L-15). */
    private static final String INTERVALO = "60s";

    private final ReservaRepositorio reservas;
    private final AvisosUsuario avisos;
    private final int loteMax;

    @Inject
    public FinalizarReservasService(
            ReservaRepositorio reservas,
            AvisosUsuario avisos,
            @ConfigProperty(name = "cd.reservas.finalizacion-lote-max", defaultValue = "200") int loteMax) {
        this.reservas = reservas;
        this.avisos = avisos;
        this.loteMax = loteMax;
    }

    /** Disparador periodico del job. */
    @Scheduled(every = INTERVALO)
    @Transactional
    void programado() {
        finalizarConcluidas(loteMax);
    }

    @Override
    @Transactional
    public int finalizarConcluidas(int max) {
        int lote = max > 0 ? max : loteMax;
        List<Long> concluidas = reservas.bloquearConfirmadasConcluidas(lote);
        for (Long idReserva : concluidas) {
            reservas.cambiarEstado(idReserva, Reserva.Estado.CONFIRMADA, Reserva.Estado.FINALIZADA, MOTIVO);
            reservas.buscar(idReserva).ifPresent(reserva ->
                    avisos.avisar(reserva.idUsuario(), "Reserva finalizada",
                            "Tu reserva #" + idReserva + " quedo finalizada. Ya puedes calificar la cancha."));
        }
        return concluidas.size();
    }
}
