package pe.com.canchadirecta.payments.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.com.canchadirecta.payments.application.port.out.PagoRepositorio;
import pe.com.canchadirecta.payments.domain.model.Pago;
import pe.com.canchadirecta.shared.application.port.out.ConfiguracionPlataforma;
import pe.com.canchadirecta.shared.application.port.out.PoliticaReembolso;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Politica economica al liberar una reserva (L-12 / CU05.3), modulo `payments`.
 *
 * <p>Reglas (documentadas en ADR-0020):</p>
 * <ul>
 *   <li><b>Rechazo del propietario:</b> reembolso total del pago aprobado, si lo hubiera.</li>
 *   <li><b>Cancelacion del titular:</b> reembolso total solo si se cancela dentro de la
 *       ventana gratuita (al menos {@code cd.pagos.horas-cancelacion-gratis} horas antes del
 *       inicio); fuera de esa ventana no genera reembolso.</li>
 *   <li>Si la reserva no tiene pago aprobado, no se hace nada.</li>
 * </ul>
 *
 * <p>Se ejecuta dentro de la transaccion que cambio el estado de la reserva, de modo que el
 * cambio de estado y el reembolso son atomicos. Como busca solo pagos en `APROBADO`, una
 * segunda liberacion de la misma reserva no vuelve a reembolsar (idempotente).</p>
 */
@ApplicationScoped
public class PoliticaReembolsoService implements PoliticaReembolso {

    private final PagoRepositorio pagos;
    private final ConfiguracionPlataforma configuracion;

    @Inject
    public PoliticaReembolsoService(PagoRepositorio pagos, ConfiguracionPlataforma configuracion) {
        this.pagos = pagos;
        this.configuracion = configuracion;
    }

    @Override
    @Transactional
    public void alLiberarReserva(Long idReserva, Instant inicioReserva, Instant momento, boolean porPropietario) {
        Optional<Pago> aprobado = pagos.pagoAprobadoDe(idReserva);
        if (aprobado.isEmpty()) {
            return;
        }
        Pago pago = aprobado.get();
        long horasCancelacionGratis = configuracion.horasCancelacionGratis();
        if (!porPropietario && momento.isAfter(inicioReserva.minus(horasCancelacionGratis, ChronoUnit.HOURS))) {
            // Cancelacion tardia: la politica no genera reembolso (L-12).
            return;
        }

        String motivo = porPropietario
                ? "Reembolso por rechazo del propietario"
                : "Reembolso por cancelacion dentro de la ventana gratuita";
        pagos.registrarReembolso(pago.id(), pago.monto(), motivo, momento);
        pagos.marcarReembolsado(pago.id());
        pagos.registrarEvento(idReserva, pago.id(), "REEMBOLSADO", motivo);
    }
}
