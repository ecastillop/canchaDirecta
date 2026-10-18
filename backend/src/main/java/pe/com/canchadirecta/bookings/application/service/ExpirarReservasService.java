package pe.com.canchadirecta.bookings.application.service;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import pe.com.canchadirecta.bookings.application.port.in.ExpirarReservasUseCase;
import pe.com.canchadirecta.bookings.application.port.out.ReservaRepositorio;
import pe.com.canchadirecta.bookings.domain.model.Reserva;

import java.util.List;

/**
 * Job de expiracion del hold de pago (ADR-0022).
 *
 * <p>Una solicitud `PENDIENTE` sin pago `APROBADO` cuyo hold de pago vencio pasa a
 * `EXPIRADA`, liberando la franja (la ocupacion solo cuenta `PENDIENTE` y `CONFIRMADA`)
 * y dejando la traza en `historial_reserva`. El adaptador toma el bloqueo exclusivo de las
 * filas afectadas (`FOR UPDATE OF r`), de modo que el propietario o el titular no pueden
 * cambiar su estado mientras el job las procesa.</p>
 *
 * <p>El disparador periodico solo delega en {@link #expirarVencidas(int)}: las pruebas
 * invocan el caso de uso de forma directa y determinista (el temporizador esta desactivado
 * en el perfil `test`).</p>
 */
@ApplicationScoped
public class ExpirarReservasService implements ExpirarReservasUseCase {

    /** Observacion con la que queda registrada la expiracion en `historial_reserva`. */
    private static final String MOTIVO = "Expirada: no se completo el pago dentro del hold";

    /** Intervalo del job. Fijo y corto: el hold se mide en minutos (ADR-0022). */
    private static final String INTERVALO = "60s";

    private final ReservaRepositorio reservas;
    private final int holdPorDefectoMin;
    private final int loteMax;

    @Inject
    public ExpirarReservasService(
            ReservaRepositorio reservas,
            @ConfigProperty(name = "cd.reservas.hold-pago-min", defaultValue = "10") int holdPorDefectoMin,
            @ConfigProperty(name = "cd.reservas.expiracion-lote-max", defaultValue = "200") int loteMax) {
        this.reservas = reservas;
        this.holdPorDefectoMin = holdPorDefectoMin;
        this.loteMax = loteMax;
    }

    /** Disparador periodico del job. */
    @Scheduled(every = INTERVALO)
    @Transactional
    void programado() {
        expirarVencidas(loteMax);
    }

    @Override
    @Transactional
    public int expirarVencidas(int max) {
        int lote = max > 0 ? max : loteMax;
        List<Long> vencidas = reservas.bloquearPendientesConHoldVencido(holdPorDefectoMin, lote);
        for (Long idReserva : vencidas) {
            reservas.cambiarEstado(idReserva, Reserva.Estado.PENDIENTE, Reserva.Estado.EXPIRADA, MOTIVO);
        }
        return vencidas.size();
    }
}
