package pe.com.canchadirecta.payments.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.com.canchadirecta.bookings.application.port.out.ReservaRepositorio;
import pe.com.canchadirecta.bookings.domain.model.Reserva;
import pe.com.canchadirecta.payments.application.dto.DetallePago;
import pe.com.canchadirecta.payments.application.port.in.PagosUseCase;
import pe.com.canchadirecta.payments.application.port.out.PagoRepositorio;
import pe.com.canchadirecta.payments.application.port.out.PaymentGatewayPort;
import pe.com.canchadirecta.payments.domain.model.Pago;
import pe.com.canchadirecta.shared.application.port.out.AvisosUsuario;
import pe.com.canchadirecta.shared.application.port.out.Reloj;
import pe.com.canchadirecta.shared.domain.DomainException;
import pe.com.canchadirecta.shared.domain.ErrorCodes;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Pago simulado de una reserva (CU03.4 / CU05.1).
 *
 * <p>El importe a cobrar es el total congelado de la reserva: `monto_cancha -
 * monto_descuento` mas el monto de la cochera opcional, si la hubiera. El resultado lo
 * decide {@link PaymentGatewayPort}, que en este stack es un simulador local determinista.</p>
 *
 * <p><b>RN-05.</b> Como maximo un pago `APROBADO` por reserva: si ya existe uno, la
 * operacion devuelve `409` sin volver a cobrar. La garantia de fondo es el `UNIQUE` de la
 * columna generada `id_reserva_aprobada` (ADR-0006).</p>
 */
@ApplicationScoped
public class PagoSimuladoService implements PagosUseCase {

    private final ReservaRepositorio reservas;
    private final PagoRepositorio pagos;
    private final PaymentGatewayPort pasarela;
    private final Reloj reloj;
    private final AvisosUsuario avisos;

    @Inject
    public PagoSimuladoService(ReservaRepositorio reservas, PagoRepositorio pagos,
                               PaymentGatewayPort pasarela, Reloj reloj, AvisosUsuario avisos) {
        this.reservas = reservas;
        this.pagos = pagos;
        this.pasarela = pasarela;
        this.reloj = reloj;
        this.avisos = avisos;
    }

    @Override
    @Transactional
    public Pago pagar(Long idUsuario, Long idReserva, String medioPago, String codigoOperacion) {
        if (idReserva == null) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "La reserva es obligatoria.");
        }
        if (medioPago == null || medioPago.isBlank()) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "El medio de pago es obligatorio.");
        }
        if (medioPago.trim().length() > Pago.MEDIO_MAXIMO) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS,
                    "El medio de pago no puede superar los " + Pago.MEDIO_MAXIMO + " caracteres.");
        }

        Reserva reserva = reservas.buscar(idReserva)
                .orElseThrow(() -> new DomainException(ErrorCodes.NO_ENCONTRADO, "La reserva no existe."));
        if (!reserva.idUsuario().equals(idUsuario)) {
            throw new DomainException(ErrorCodes.SIN_PERMISO, "Solo el titular puede pagar su reserva.");
        }
        if (!admitePago(reserva.estado())) {
            throw new DomainException(ErrorCodes.CONFLICTO,
                    "Una reserva " + reserva.estado() + " no admite pago.");
        }
        if (pagos.pagoAprobadoDe(idReserva).isPresent()) {
            throw new DomainException(ErrorCodes.CONFLICTO, "La reserva ya tiene un pago aprobado.");
        }

        BigDecimal monto = montoTotal(reserva);
        Instant ahora = reloj.ahora();
        pagos.registrarEvento(idReserva, null, "SOLICITADO", "Medio: " + medioPago.trim());

        PaymentGatewayPort.ResultadoCobro cobro = pasarela.cobrar(monto, medioPago.trim(), codigoOperacion);
        Pago.Estado estado = cobro.aprobado() ? Pago.Estado.APROBADO : Pago.Estado.RECHAZADO;
        Long idPago = pagos.registrarPago(idReserva, monto, medioPago.trim(), cobro.codigoOperacion(),
                estado, ahora);
        pagos.registrarEvento(idReserva, idPago, estado.name(),
                estado == Pago.Estado.APROBADO ? "Cobro aprobado por el simulador" : "Cobro rechazado por el simulador");
        if (estado == Pago.Estado.APROBADO) {
            avisos.avisar(idUsuario, "Pago aprobado",
                    "Registramos el pago de tu reserva #" + idReserva + " por " + monto + ".");
        }

        return new Pago(idPago, idReserva, monto, medioPago.trim(), cobro.codigoOperacion(), estado, ahora);
    }

    @Override
    public DetallePago consultar(Long idUsuario, Long idReserva) {
        Reserva reserva = reservas.buscar(idReserva)
                .orElseThrow(() -> new DomainException(ErrorCodes.NO_ENCONTRADO, "La reserva no existe."));
        if (!reserva.idUsuario().equals(idUsuario)) {
            throw new DomainException(ErrorCodes.SIN_PERMISO, "Solo el titular puede consultar su pago.");
        }
        Pago pago = pagos.ultimoPagoDe(idReserva)
                .orElseThrow(() -> new DomainException(ErrorCodes.NO_ENCONTRADO,
                        "La reserva aun no tiene pagos."));
        return new DetallePago(pago, pagos.reembolsosDePago(pago.id()));
    }

    /** Una reserva admite pago mientras siga vigente (no cancelada, rechazada ni finalizada). */
    private static boolean admitePago(Reserva.Estado estado) {
        return estado == Reserva.Estado.PENDIENTE || estado == Reserva.Estado.CONFIRMADA;
    }

    /** Total congelado de la reserva: cancha con descuento + cochera opcional. */
    private static BigDecimal montoTotal(Reserva reserva) {
        BigDecimal total = reserva.montoCancha().subtract(reserva.montoDescuento());
        Reserva.CocheraReservada cochera = reserva.cochera();
        return cochera == null ? total : total.add(cochera.monto());
    }
}
