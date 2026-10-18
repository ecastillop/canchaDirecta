package pe.com.canchadirecta.bookings.application.port.out;

import pe.com.canchadirecta.bookings.application.dto.CanchaInfo;
import pe.com.canchadirecta.bookings.application.dto.CocheraInfo;
import pe.com.canchadirecta.bookings.application.dto.HorarioSemanal;
import pe.com.canchadirecta.bookings.domain.model.FranjaHoraria;
import pe.com.canchadirecta.bookings.domain.model.Reserva;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Puerto de salida de reservas.
 *
 * <p>Contrato de concurrencia (RN-01): `bloquearCancha` debe ejecutarse dentro de la
 * transaccion del caso de uso y tomar un bloqueo exclusivo de la fila de la cancha
 * (`SELECT ... FOR UPDATE`) que se mantiene hasta el commit. Con el bloqueo tomado,
 * las comprobaciones de solapamiento y el INSERT quedan serializados por cancha, de
 * modo que N peticiones simultaneas producen como maximo una reserva.</p>
 *
 * <p>Los cambios de estado siguen el mismo patron sobre la fila de la reserva
 * (`buscarParaActualizar` + `cambiarEstado`), de modo que dos cancelaciones
 * simultaneas de la misma reserva no pueden registrarse dos veces.</p>
 */
public interface ReservaRepositorio {

    /** Toma el bloqueo exclusivo de la fila de la cancha y devuelve sus datos. */
    Optional<CanchaInfo> bloquearCancha(Long idCancha);

    /** Lee los datos de la cancha sin tomar bloqueo (consultas de disponibilidad). */
    Optional<CanchaInfo> buscarCancha(Long idCancha);

    /**
     * Horarios de atencion activos de la cancha, de toda la semana. Una lista vacia
     * significa que la cancha aun no tiene agenda configurada.
     */
    List<HorarioSemanal> horariosDe(Long idCancha);

    /** Reservas vigentes y bloqueos operativos que intersectan `[desde, hasta)`. */
    List<FranjaHoraria> ocupacionEn(Long idCancha, Instant desde, Instant hasta);

    /** ?Existe una reserva vigente (PENDIENTE/CONFIRMADA) que se solape? */
    boolean existeReservaVigenteSolapada(Long idCancha, Instant inicio, Instant fin);

    /** ?Existe un bloqueo operativo del establecimiento que se solape? */
    boolean existeBloqueoSolapado(Long idCancha, Instant inicio, Instant fin);

    /** Inserta la reserva y su entrada de historial; devuelve el id generado. */
    Long registrar(Long idUsuario, Long idCancha, Long idPromocion, FranjaHoraria franja,
                   BigDecimal monto, BigDecimal montoDescuento, String claveIdempotencia);

    /** Reserva ya creada con esa clave de idempotencia para el titular (CU03.2). */
    Optional<Reserva> buscarPorClaveIdempotencia(Long idUsuario, String claveIdempotencia);

    /** Reprograma inicio/fin (y la cochera asociada) conservando importes (CU03.7). */
    void moverFranja(Long idReserva, Instant inicio, Instant fin);

    /** Como `existeReservaVigenteSolapada`, ignorando la propia reserva (reprogramacion). */
    boolean existeReservaVigenteSolapadaExcluyendo(Long idCancha, Instant inicio, Instant fin,
                                                   Long idReservaExcluir);

    /** Lee la reserva con bloqueo exclusivo de su fila para poder cambiarle el estado. */
    Optional<Reserva> buscarParaActualizar(Long idReserva);

    /**
     * Lee la reserva sin tomar bloqueo. Se usa en los casos de uso que no cambian su estado
     * (p. ej. el pago simulado), donde la consistencia frente a escrituras la da el esquema
     * (indices unicos) y no un bloqueo pesimista.
     */
    Optional<Reserva> buscar(Long idReserva);

    /** Cambia el estado de la reserva y deja la traza en `historial_reserva`. */
    void cambiarEstado(Long idReserva, Reserva.Estado anterior, Reserva.Estado nuevo, String observacion);

    List<Reserva> listarPorUsuario(Long idUsuario, int limite);

    /**
     * ?La reserva pertenece a una cancha de un establecimiento del usuario? El
     * propietario solo atiende reservas de sus propias canchas (403 en caso contrario).
     */
    boolean esPropietarioDeReserva(Long idReserva, Long idUsuario);

    /**
     * Reservas recibidas por todas las canchas del propietario, mas recientes primero.
     * `estado` nulo devuelve todos los estados.
     */
    List<Reserva> listarPorPropietario(Long idUsuario, Reserva.Estado estado, int limite);

    // --- Expiracion del hold de pago (ADR-0022) -------------------------------

    /**
     * Ids de reservas `PENDIENTE` sin pago `APROBADO` cuyo hold de pago ya vencio
     * (`fecha_creacion + hold_pago_min` del establecimiento de su cancha, o
     * `holdDefectoMin` si no hay `config_establecimiento`), con bloqueo exclusivo de sus
     * filas (`FOR UPDATE`) para expirarlas de forma segura: el propietario o el titular no
     * pueden cambiar su estado mientras el job las procesa.
     *
     * <p>La antiguedad se mide contra el reloj de la base (`NOW(6)`), que es el mismo que
     * llena `fecha_creacion` por defecto; asi no se mezclan marcos horarios entre la
     * columna y el instante de referencia (L-04).</p>
     *
     * @param holdDefectoMin hold por defecto en minutos si el establecimiento no lo define
     * @param max            maximo de reservas a devolver (acota el lote)
     */
    List<Long> bloquearPendientesConHoldVencido(int holdDefectoMin, int max);

    // --- Cierre de reservas concluidas (L-15) ---------------------------------

    /**
     * Ids de reservas `CONFIRMADA` cuyo bloque ya concluyo (`fin` anterior al instante
     * actual), con bloqueo exclusivo de sus filas (`FOR UPDATE`) para cerrarlas de forma
     * segura: el propietario no puede revocarlas mientras el job las procesa.
     *
     * <p>`inicio`/`fin` se almacenan como literales UTC (ADR-0014), por lo que la
     * comparacion usa el instante actual en UTC (`UTC_TIMESTAMP(6)`) y no `NOW(6)` (hora
     * local del servidor): comparar el literal UTC contra la hora local desviaria el cierre
     * por el desfase de zona (L-04).</p>
     *
     * @param max maximo de reservas a devolver (acota el lote)
     */
    List<Long> bloquearConfirmadasConcluidas(int max);

    // --- Cochera opcional (RN-08) ----------------------------------------------

    /**
     * Toma el bloqueo exclusivo de la fila de la cochera (`SELECT ... FOR UPDATE`) y la
     * devuelve. Debe invocarse **despues** de `bloquearCancha` para respetar un orden de
     * locking consistente (cancha -> cochera) y evitar deadlocks.
     */
    Optional<CocheraInfo> bloquearCochera(Long idCochera);

    /** Cocheras activas de un establecimiento (para ofrecer la cochera opcional). */
    List<CocheraInfo> cocherasDeEstablecimiento(Long idEstablecimiento);

    /**
     * Cuenta las reservas de cochera vigentes (`PENDIENTE`/`CONFIRMADA` en su reserva) que
     * se solapan con `[inicio, fin)`. Lectura bloqueante para leer la ultima version
     * confirmada bajo el lock de la cochera.
     */
    int ocupacionCochera(Long idCochera, Instant inicio, Instant fin);

    /** Inserta la `reserva_cochera` de la reserva con su monto congelado. */
    Long registrarReservaCochera(Long idReserva, Long idCochera, Instant inicio, Instant fin, BigDecimal monto);
}
