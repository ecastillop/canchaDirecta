package pe.com.canchadirecta.payments.application.port.out;

import pe.com.canchadirecta.payments.domain.model.Pago;
import pe.com.canchadirecta.payments.domain.model.Reembolso;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Puerto de salida de pagos (SQL nativo, ADR-0012).
 *
 * <p>La unicidad de un pago `APROBADO` por reserva (RN-05) la impone el esquema: columna
 * generada `id_reserva_aprobada` + `UNIQUE` (ADR-0006). El repositorio no necesita tomar
 * bloqueos: la base de datos es la garantia final ante dos pagos simultaneos.</p>
 */
public interface PagoRepositorio {

    /** Pago mas reciente de la reserva (cualquier estado), si existe. */
    Optional<Pago> ultimoPagoDe(Long idReserva);

    /** Pago en estado `APROBADO` de la reserva, si existe (como mucho uno por RN-05). */
    Optional<Pago> pagoAprobadoDe(Long idReserva);

    /** Inserta el pago con su fecha en UTC y devuelve el id generado. */
    Long registrarPago(Long idReserva, BigDecimal monto, String medioPago, String codigoOperacion,
                       Pago.Estado estado, Instant momento);

    /** Registra un evento del ciclo del pago (`pago_evento`); `idPago` puede ser nulo. */
    void registrarEvento(Long idReserva, Long idPago, String tipo, String detalle);

    /** Marca el pago como `REEMBOLSADO`. */
    void marcarReembolsado(Long idPago);

    /** Inserta el reembolso ya resuelto (`PROCESADO`) y devuelve su id. */
    Long registrarReembolso(Long idPago, BigDecimal monto, String motivo, Instant momento);

    /** Reembolsos de un pago, mas recientes primero. */
    List<Reembolso> reembolsosDePago(Long idPago);
}
