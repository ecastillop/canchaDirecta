package pe.com.canchadirecta.bookings.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import pe.com.canchadirecta.bookings.application.dto.CanchaInfo;
import pe.com.canchadirecta.bookings.application.dto.CocheraInfo;
import pe.com.canchadirecta.bookings.application.dto.CrearReservaCommand;
import pe.com.canchadirecta.bookings.application.port.in.ReservasUseCase;
import pe.com.canchadirecta.bookings.application.port.out.ReservaRepositorio;
import pe.com.canchadirecta.bookings.domain.model.FranjaHoraria;
import pe.com.canchadirecta.bookings.domain.model.Reserva;
import pe.com.canchadirecta.shared.application.port.out.ConfiguracionPlataforma;
import pe.com.canchadirecta.shared.application.port.out.PromocionesCancha;
import pe.com.canchadirecta.shared.application.port.out.Reloj;
import pe.com.canchadirecta.shared.domain.DomainException;
import pe.com.canchadirecta.shared.domain.ErrorCodes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Creacion de reservas con garantia de unicidad por franja (RN-01) y cochera opcional
 * con control de capacidad (RN-08).
 *
 * <p>El metodo es transaccional y el orden de operaciones es deliberado:</p>
 * <ol>
 *   <li>Bloquear la fila de la cancha (`SELECT ... FOR UPDATE`). Esto serializa todas
 *       las peticiones de esa cancha: la segunda espera hasta que la primera confirma.</li>
 *   <li>Comprobar solapamientos (reservas vigentes y bloqueos operativos).</li>
 *   <li>Si se eligio cochera, bloquear su fila en un orden consistente
 *       (cancha -> cochera), comprobar que pertenece al mismo establecimiento y que hay
 *       cupo en la franja; en caso contrario, `409`.</li>
 *   <li>Insertar reserva + historial (y `reserva_cochera` si aplica).</li>
 * </ol>
 * <p>Como la comprobacion y el INSERT ocurren con los bloqueos tomados, no existe ventana
 * de carrera: con N peticiones simultaneas exactamente una obtiene 201 y el resto 409.</p>
 */
@ApplicationScoped
public class CrearReservaService implements ReservasUseCase {

    private static final ZoneId ZONA_NEGOCIO = ZoneId.of("America/Lima");

    private final ReservaRepositorio reservas;
    private final PromocionesCancha promociones;
    private final Reloj reloj;
    private final ConfiguracionPlataforma configuracion;

    @Inject
    public CrearReservaService(ReservaRepositorio reservas, PromocionesCancha promociones, Reloj reloj,
                               ConfiguracionPlataforma configuracion) {
        this.reservas = reservas;
        this.promociones = promociones;
        this.reloj = reloj;
        this.configuracion = configuracion;
    }

    @Override
    @Transactional
    public Reserva crear(CrearReservaCommand command) {
        if (command.idCancha() == null) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "La cancha es obligatoria.");
        }
        String clave = normalizarClave(command.claveIdempotencia());
        if (clave != null) {
            Optional<Reserva> previa = reservas.buscarPorClaveIdempotencia(command.idUsuario(), clave);
            if (previa.isPresent()) {
                return previa.get();
            }
        }
        // Valida rango y duracion (30-180 min).
        FranjaHoraria franja = new FranjaHoraria(command.inicio(), command.fin());

        Instant ahora = reloj.ahora();
        long diasAnticipacionMax = configuracion.diasAnticipacionMax();
        if (franja.inicio().isBefore(ahora)) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "No se puede reservar en el pasado.");
        }
        if (franja.inicio().isAfter(ahora.plus(diasAnticipacionMax, ChronoUnit.DAYS))) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS,
                    "Solo se puede reservar con " + diasAnticipacionMax + " dias de anticipacion como maximo.");
        }

        CanchaInfo cancha = reservas.bloquearCancha(command.idCancha())
                .orElseThrow(() -> new DomainException(ErrorCodes.NO_ENCONTRADO, "La cancha no existe."));
        if (!cancha.reservable()) {
            throw new DomainException(ErrorCodes.FRANJA_NO_DISPONIBLE, "La cancha no esta disponible.");
        }

        // Cochera opcional (RN-08). Se bloquea su fila justo despues de la cancha (orden de
        // locking consistente cancha -> cochera) y **antes** de las lecturas bloqueantes de
        // `reserva`/`bloqueo_cancha`: asi varias peticiones que compiten por la misma cochera
        // (en canchas distintas) se serializan en la cochera y no pueden formar un ciclo de
        // locks (deadlock) con los bloqueos de rango de `reserva`.
        CocheraInfo cochera = bloquearCochera(command, cancha);

        if (reservas.existeReservaVigenteSolapada(command.idCancha(), franja.inicio(), franja.fin())) {
            throw new DomainException(ErrorCodes.FRANJA_NO_DISPONIBLE, "La franja ya esta reservada.");
        }
        if (reservas.existeBloqueoSolapado(command.idCancha(), franja.inicio(), franja.fin())) {
            throw new DomainException(ErrorCodes.FRANJA_NO_DISPONIBLE,
                    "La franja esta bloqueada por el establecimiento.");
        }

        Reserva.CocheraReservada cocheraReservada = comprobarCupoYCobrar(cochera, franja);
        Descuento descuento = calcularDescuento(command.codigoPromocion(), command.idCancha(),
                cancha.precioBase(), franja.inicio(), command.idUsuario());
        Long idPromocion = descuento.promocion() == null ? null : descuento.promocion().id();

        Long id;
        try {
            id = reservas.registrar(command.idUsuario(), command.idCancha(), idPromocion, franja,
                    cancha.precioBase(), descuento.monto(), clave);
        } catch (PersistenceException ex) {
            // Carrera de dos peticiones con la misma clave: gana la que inserto primero.
            if (clave != null) {
                Optional<Reserva> previa = reservas.buscarPorClaveIdempotencia(command.idUsuario(), clave);
                if (previa.isPresent()) {
                    return previa.get();
                }
            }
            throw ex;
        }
        if (descuento.promocion() != null
                && !promociones.consumir(descuento.promocion().id(), command.idUsuario())) {
            throw new DomainException(ErrorCodes.CONFLICTO,
                    "El cupon alcanzo su limite de usos. Intenta con otro codigo.");
        }
        if (cocheraReservada != null) {
            reservas.registrarReservaCochera(id, cocheraReservada.idCochera(), franja.inicio(), franja.fin(),
                    cocheraReservada.monto());
        }
        return new Reserva(id, command.idUsuario(), command.idCancha(), franja,
                cancha.precioBase(), descuento.monto(), Reserva.Estado.PENDIENTE, ahora, cocheraReservada);
    }

    /**
     * Descuento de la promocion indicada, limitado al precio de la cancha y evaluado contra la
     * **fecha de la reserva** (no la de hoy). Una promocion ausente, inactiva, fuera de fechas o no
     * asociada a la cancha es `409` (el usuario puede corregir el codigo o reservar sin el).
     */
    private Descuento calcularDescuento(String codigo, Long idCancha, BigDecimal precio,
                                        Instant inicioFranja, Long idUsuario) {
        if (codigo == null || codigo.isBlank()) {
            return new Descuento(null, BigDecimal.ZERO);
        }
        String limpio = codigo.trim().toUpperCase(Locale.ROOT);
        PromocionesCancha.Promocion promocion = promociones
                .vigente(limpio, idCancha, LocalDate.ofInstant(inicioFranja, ZONA_NEGOCIO), idUsuario)
                .orElseThrow(() -> new DomainException(ErrorCodes.CONFLICTO,
                        "El cupon no existe, no esta vigente, no aplica a esta cancha o agoto sus usos."));
        BigDecimal descuento = "PORCENTAJE".equals(promocion.tipoDescuento())
                ? precio.multiply(promocion.valor()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                : promocion.valor();
        if (descuento.compareTo(precio) > 0) {
            descuento = precio;
        }
        return new Descuento(promocion, descuento.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));
    }

    private record Descuento(PromocionesCancha.Promocion promocion, BigDecimal monto) {
    }

    /**
     * Si la peticion elige cochera, bloquea su fila y exige que sea del mismo
     * establecimiento que la cancha y que este activa; devuelve `null` si no se pidio cochera.
     */
    private CocheraInfo bloquearCochera(CrearReservaCommand command, CanchaInfo cancha) {
        if (command.idCochera() == null) {
            return null;
        }
        CocheraInfo cochera = reservas.bloquearCochera(command.idCochera())
                .orElseThrow(() -> new DomainException(ErrorCodes.NO_ENCONTRADO, "La cochera no existe."));
        if (!cochera.idEstablecimiento().equals(cancha.idEstablecimiento())) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS,
                    "La cochera no pertenece al establecimiento de la cancha.");
        }
        if (!cochera.activa()) {
            throw new DomainException(ErrorCodes.FRANJA_NO_DISPONIBLE, "La cochera no esta disponible.");
        }
        return cochera;
    }

    /** Con la cochera ya bloqueada, comprueba el cupo de la franja y calcula el monto. */
    private Reserva.CocheraReservada comprobarCupoYCobrar(CocheraInfo cochera, FranjaHoraria franja) {
        if (cochera == null) {
            return null;
        }
        if (reservas.ocupacionCochera(cochera.id(), franja.inicio(), franja.fin()) >= cochera.capacidad()) {
            throw new DomainException(ErrorCodes.FRANJA_NO_DISPONIBLE,
                    "La cochera no tiene cupo en esa franja.");
        }
        return new Reserva.CocheraReservada(cochera.id(), montoCochera(cochera.tarifaHora(), franja));
    }

    /** Tarifa por hora x duracion en horas; redondeo a 2 decimales (HALF_UP). */
    private static BigDecimal montoCochera(BigDecimal tarifaHora, FranjaHoraria franja) {
        return tarifaHora.multiply(BigDecimal.valueOf(franja.minutos()))
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }

    @Override
    @Transactional
    public Reserva modificar(Long idUsuario, Long idReserva, Instant inicio, Instant fin) {
        Reserva vista = reservas.buscar(idReserva)
                .orElseThrow(() -> new DomainException(ErrorCodes.NO_ENCONTRADO,
                        "La reserva no existe."));
        if (!vista.idUsuario().equals(idUsuario)) {
            throw new DomainException(ErrorCodes.SIN_PERMISO,
                    "La reserva pertenece a otro usuario.");
        }
        if (!Reserva.ocupaFranja(vista.estado())) {
            throw new DomainException(ErrorCodes.CONFLICTO,
                    "Solo se pueden reprogramar reservas vigentes.");
        }

        FranjaHoraria nueva = new FranjaHoraria(inicio, fin);
        if (nueva.minutos() != vista.franja().minutos()) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS,
                    "La reprogramacion debe conservar la duracion de la reserva.");
        }
        Instant ahora = reloj.ahora();
        long diasAnticipacionMax = configuracion.diasAnticipacionMax();
        if (nueva.inicio().isBefore(ahora)) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "No se puede reprogramar al pasado.");
        }
        if (nueva.inicio().isAfter(ahora.plus(diasAnticipacionMax, ChronoUnit.DAYS))) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS,
                    "Solo se puede reservar con " + diasAnticipacionMax
                            + " dias de anticipacion como maximo.");
        }

        // Orden de locking consistente con `crear`: cancha -> cochera -> reserva.
        CanchaInfo cancha = reservas.bloquearCancha(vista.idCancha())
                .orElseThrow(() -> new DomainException(ErrorCodes.NO_ENCONTRADO, "La cancha no existe."));
        if (!cancha.reservable()) {
            throw new DomainException(ErrorCodes.FRANJA_NO_DISPONIBLE, "La cancha no esta disponible.");
        }
        CocheraInfo cochera = null;
        if (vista.cochera() != null) {
            cochera = reservas.bloquearCochera(vista.cochera().idCochera())
                    .orElseThrow(() -> new DomainException(ErrorCodes.NO_ENCONTRADO,
                            "La cochera no existe."));
            if (!cochera.activa()) {
                throw new DomainException(ErrorCodes.FRANJA_NO_DISPONIBLE,
                        "La cochera no esta disponible.");
            }
        }

        Reserva actual = reservas.buscarParaActualizar(idReserva)
                .orElseThrow(() -> new DomainException(ErrorCodes.NO_ENCONTRADO,
                        "La reserva no existe."));
        if (!Reserva.ocupaFranja(actual.estado())) {
            throw new DomainException(ErrorCodes.CONFLICTO,
                    "Solo se pueden reprogramar reservas vigentes.");
        }
        if (reservas.existeReservaVigenteSolapadaExcluyendo(actual.idCancha(), nueva.inicio(),
                nueva.fin(), idReserva)) {
            throw new DomainException(ErrorCodes.FRANJA_NO_DISPONIBLE, "La franja ya esta reservada.");
        }
        if (reservas.existeBloqueoSolapado(actual.idCancha(), nueva.inicio(), nueva.fin())) {
            throw new DomainException(ErrorCodes.FRANJA_NO_DISPONIBLE,
                    "La franja esta bloqueada por el establecimiento.");
        }
        if (cochera != null && reservas.ocupacionCochera(cochera.id(), nueva.inicio(), nueva.fin())
                >= cochera.capacidad()) {
            throw new DomainException(ErrorCodes.FRANJA_NO_DISPONIBLE,
                    "La cochera no tiene cupo en esa franja.");
        }

        reservas.moverFranja(idReserva, nueva.inicio(), nueva.fin());
        return new Reserva(actual.id(), actual.idUsuario(), actual.idCancha(), nueva,
                actual.montoCancha(), actual.montoDescuento(), actual.estado(),
                actual.fechaCreacion(), actual.cochera());
    }

    private static String normalizarClave(String clave) {
        if (clave == null || clave.isBlank()) {
            return null;
        }
        String valor = clave.trim();
        if (valor.length() > 80) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS,
                    "La clave de idempotencia supera 80 caracteres.");
        }
        return valor;
    }

    @Override
    public List<Reserva> listarMias(Long idUsuario, int limite) {
        return reservas.listarPorUsuario(idUsuario, limite);
    }
}
