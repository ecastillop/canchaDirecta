package pe.com.canchadirecta.venues.application.port.out;

import pe.com.canchadirecta.venues.domain.model.BloqueoCancha;
import pe.com.canchadirecta.venues.domain.model.Cancha;
import pe.com.canchadirecta.venues.domain.model.CatalogoCancha;
import pe.com.canchadirecta.venues.domain.model.Cochera;
import pe.com.canchadirecta.venues.domain.model.Establecimiento;
import pe.com.canchadirecta.venues.domain.model.HorarioCancha;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Puerto de salida: persistencia de establecimientos y canchas. */
public interface VenuesRepositorio {

    /** Devuelve el `id_propietario` del usuario, creandolo si aun no existe. */
    Long asegurarPropietario(Long idUsuario, String documentoIdentidad);

    Long crearEstablecimiento(Long idPropietario, Long idDistrito, String nombre,
                              String direccion, String referencia);

    Optional<Establecimiento> buscarEstablecimiento(Long id);

    /** Establecimientos del usuario, activos primero y luego por nombre. */
    List<Establecimiento> listarEstablecimientosDeUsuario(Long idUsuario);

    /** Actualiza los datos editables; `false` si el establecimiento no existe. */
    boolean actualizarEstablecimiento(Long idEstablecimiento, Long idDistrito, String nombre,
                                      String direccion, String referencia);

    /**
     * Baja logica: `false` si no existe o ya estaba inactivo. Quien la invoca debe comprobar
     * antes que no haya reservas vigentes pendientes.
     */
    boolean desactivarEstablecimiento(Long idEstablecimiento);

    /** Baja logica de todas las canchas del establecimiento; devuelve cuantas cambio. */
    int desactivarCanchasDeEstablecimiento(Long idEstablecimiento);

    /** Indica si el establecimiento tiene reservas vigentes cuya franja aun no ha terminado. */
    boolean tieneReservasVigentesFuturasDeEstablecimiento(Long idEstablecimiento);

    /** Indica si el establecimiento pertenece al usuario (autorizacion por recurso). */
    boolean esPropietarioDe(Long idEstablecimiento, Long idUsuario);

    Long crearCancha(Long idEstablecimiento, String nombre, String descripcion,
                     int capacidad, BigDecimal precioBase);

    Optional<Cancha> buscarCancha(Long id);

    /** Actualiza los datos editables de la cancha; `false` si no existe. */
    boolean actualizarCancha(Long idCancha, String nombre, String descripcion, int capacidad,
                             BigDecimal precioBase);

    /** `true` si otra cancha del mismo establecimiento ya usa ese nombre. */
    boolean existeOtraCanchaConNombre(Long idEstablecimiento, String nombre, Long idCanchaExcluir);

    /**
     * Baja logica: `false` si no existe o ya estaba inactiva. Quien la invoca debe comprobar
     * antes que no haya reservas vigentes pendientes.
     */
    boolean desactivarCancha(Long idCancha);

    /** Indica si la cancha tiene reservas vigentes cuya franja aun no ha terminado. */
    boolean tieneReservasVigentesFuturas(Long idCancha);

    List<Cancha> listarCanchas(Long idDistrito, int limite);

    // --- Favoritos del cliente -------------------------------------------------
    List<Long> listarIdsFavoritos(Long idUsuario);

    void agregarFavorito(Long idUsuario, Long idCancha);

    void quitarFavorito(Long idUsuario, Long idCancha);

    /** Listado publico con filtros por distrito, deporte, precio maximo y texto. */
    List<CatalogoCancha.Resumen> buscarCanchas(CatalogoCancha.Filtro filtro);

    long contarCanchas(CatalogoCancha.Filtro filtro);

    /** Autorizacion por recurso: la cancha pertenece a un establecimiento del usuario. */
    boolean esPropietarioDeCancha(Long idCancha, Long idUsuario);

    /**
     * Autorizacion de la agenda operativa: propietario del establecimiento o personal
     * asignado a la sede (operador de recepcion, `operador_establecimiento`). El dia a dia
     * de la sede no puede depender de que el propietario este disponible (RN-03, ADR-0042).
     * No aplica a la configuracion comercial (horarios y precios), que sigue siendo del
     * propietario.
     */
    boolean esGestorDeCancha(Long idCancha, Long idUsuario);

    /**
     * Toma el bloqueo exclusivo de la fila de la cancha (`SELECT ... FOR UPDATE`) y la
     * devuelve. Es el mismo bloqueo que usa RN-01, reutilizado para que un bloqueo
     * operativo no pueda colarse entre la comprobacion de solapes y el INSERT de una
     * reserva (ADR-0017).
     */
    Optional<Cancha> bloquearCancha(Long idCancha);

    // --- Agenda: horarios semanales (HU-005) y bloqueos operativos (RN-03) -------

    /** Horarios activos de la cancha, ordenados por dia y hora de inicio. */
    List<HorarioCancha> listarHorarios(Long idCancha);

    Long crearHorario(HorarioCancha horario);

    /** Baja logica del horario; devuelve `false` si no existe o ya estaba inactivo. */
    boolean desactivarHorario(Long idCancha, Long idHorario);

    /** Bloqueos que intersectan `[desde, hasta)`, ordenados por inicio. */
    List<BloqueoCancha> listarBloqueos(Long idCancha, Instant desde, Instant hasta);

    Long crearBloqueo(BloqueoCancha bloqueo);

    /** Devuelve `false` si el bloqueo no existe en esa cancha. */
    boolean eliminarBloqueo(Long idCancha, Long idBloqueo);

    boolean existeBloqueoSolapado(Long idCancha, Instant inicio, Instant fin);

    /** Reserva vigente (`PENDIENTE`/`CONFIRMADA`) que choca con la franja. */
    boolean existeReservaVigenteSolapada(Long idCancha, Instant inicio, Instant fin);

    // --- Cocheras del establecimiento (RN-08, HU-005-2) -------------------------

    /** Registra la cochera (siempre en estado `ACTIVA`) y devuelve su id. */
    Long crearCochera(Cochera cochera);

    /** Cocheras de un establecimiento, activas primero y luego por id. */
    List<Cochera> listarCocheras(Long idEstablecimiento);

    Optional<Cochera> buscarCochera(Long id);

    /** Baja logica: `false` si la cochera no existe en ese establecimiento o ya estaba inactiva. */
    boolean desactivarCochera(Long idEstablecimiento, Long idCochera);
}
