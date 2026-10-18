package pe.com.canchadirecta.platform.application.port.out;

import pe.com.canchadirecta.platform.domain.model.Auditoria;
import pe.com.canchadirecta.platform.domain.model.CocheraAdmin;
import pe.com.canchadirecta.platform.domain.model.PromocionAdmin;
import pe.com.canchadirecta.platform.domain.model.PropietarioAdmin;
import pe.com.canchadirecta.platform.domain.model.ReporteAdmin;
import pe.com.canchadirecta.platform.domain.model.SupervisionAdmin;
import pe.com.canchadirecta.platform.domain.model.UsuarioAdmin;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Consultas y cambios de administracion de plataforma con SQL nativo (ADR-0012).
 */
public interface AdminPlataformaRepositorio {

    List<PropietarioAdmin> propietarios(String estado, int limite);

    Optional<PropietarioAdmin> propietario(Long idPropietario);

    /** Propietario asociado al usuario, si ya existe (solicitud o validacion previa). */
    Optional<PropietarioAdmin> propietarioDeUsuario(Long idUsuario);

    /** `true` si el documento ya pertenece a otro propietario. */
    boolean documentoPropietarioExiste(String documento);

    /** Crea la solicitud de propietario en estado PENDIENTE y devuelve su id (CU01.6). */
    Long registrarSolicitudPropietario(Long idUsuario, String documento);

    /** Cambia la validacion solo si sigue en el estado esperado; `false` si otra accion gano. */
    boolean cambiarValidacion(Long idPropietario, String esperado, String nuevo);

    List<SupervisionAdmin.Establecimiento> establecimientos(String estado, int limite);

    Optional<SupervisionAdmin.Establecimiento> establecimiento(Long idEstablecimiento);

    boolean cambiarEstadoEstablecimiento(Long idEstablecimiento, String esperado, String nuevo);

    List<SupervisionAdmin.Reserva> reservas(String estado, int limite);

    List<SupervisionAdmin.Pago> pagos(String estado, int limite);

    List<Auditoria> auditoria(int limite);

    /** Reporte agregado de plataforma para la ventana indicada. */
    ReporteAdmin reporte(int dias, Instant desde, Instant hasta);

    /** Usuarios con sus roles; filtros opcionales por correo (contiene), estado y rol. */
    List<UsuarioAdmin> usuarios(String correo, String estado, String rol, int limite);

    /** Usuario por id con sus roles. */
    Optional<UsuarioAdmin> usuario(Long idUsuario);

    /** Reemplaza los roles del usuario. */
    void reemplazarRoles(Long idUsuario, Set<String> roles);

    /** Cambia el estado solo si sigue en el esperado; `false` si otra accion gano. */
    boolean cambiarEstado(Long idUsuario, String esperado, String nuevo);

    /** Numero de administradores activos distintos del usuario indicado. */
    long contarOtrosAdminsActivos(Long idUsuarioExcluido);

    List<PromocionAdmin> promociones(String estado, int limite);

    Optional<PromocionAdmin> promocion(Long idPromocion);

    boolean codigoPromocionExiste(String codigo);

    /** Cuenta cuantas de las canchas indicadas existen. */
    long contarCanchas(List<Long> idsCancha);

    PromocionAdmin registrarPromocion(PromocionAdmin promocion);

    boolean cambiarEstadoPromocion(Long idPromocion, String esperado, String nuevo);

    List<CocheraAdmin> cocheras(String estado, int limite);

    Optional<CocheraAdmin> cochera(Long idCochera);

    boolean cambiarEstadoCochera(Long idCochera, String esperado, String nuevo);

    /** `true` si el establecimiento pertenece al usuario indicado. */
    boolean esPropietarioDeEstablecimiento(Long idEstablecimiento, Long idUsuario);

    /** Canchas activas o no del establecimiento, en orden de id. */
    List<Long> canchasDeEstablecimiento(Long idEstablecimiento);

    /** Promociones que tocan al menos una cancha del establecimiento. */
    List<PromocionAdmin> promocionesDeEstablecimiento(Long idEstablecimiento, int limite);

    /** `true` si la promocion esta asociada a alguna cancha del establecimiento. */
    boolean promocionDeEstablecimiento(Long idPromocion, Long idEstablecimiento);

    /** Invalida los access tokens ya emitidos del usuario (L-20). */
    void invalidarTokensDeUsuario(Long idUsuario);
}
