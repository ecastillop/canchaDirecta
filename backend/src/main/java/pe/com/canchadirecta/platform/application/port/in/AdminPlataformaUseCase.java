package pe.com.canchadirecta.platform.application.port.in;

import pe.com.canchadirecta.platform.domain.model.Auditoria;
import pe.com.canchadirecta.platform.domain.model.CocheraAdmin;
import pe.com.canchadirecta.platform.domain.model.PromocionAdmin;
import pe.com.canchadirecta.platform.domain.model.PropietarioAdmin;
import pe.com.canchadirecta.platform.domain.model.SupervisionAdmin;
import pe.com.canchadirecta.platform.domain.model.UsuarioAdmin;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Casos de uso de administracion de plataforma (F07): validacion de propietarios, supervision de
 * solo lectura y auditoria.
 */
public interface AdminPlataformaUseCase {

    /** Propietarios, opcionalmente filtrados por estado de validacion. */
    List<PropietarioAdmin> propietarios(String estado, int limite);

    /** Valida o rechaza un propietario, avisa al usuario y deja traza en auditoria. */
    PropietarioAdmin cambiarValidacion(Long idPropietario, Long idAdmin, String estado, String motivo);

    /** Establecimientos con propietario, distrito y numero de canchas. */
    List<SupervisionAdmin.Establecimiento> establecimientos(String estado, int limite);

    /** Reservas de todos los usuarios. */
    List<SupervisionAdmin.Reserva> reservas(String estado, int limite);

    /** Pagos con el total reembolsado. */
    List<SupervisionAdmin.Pago> pagos(String estado, int limite);

    /** Ultimas entradas de auditoria. */
    List<Auditoria> auditoria(int limite);

    /** Usuarios con sus roles; filtros opcionales por correo (contiene), estado y rol. */
    List<UsuarioAdmin> usuarios(String correo, String estado, String rol, int limite);

    /** Usuario por id con sus roles. */
    UsuarioAdmin usuario(Long idUsuario);

    /** Reemplaza los roles del usuario (no puede dejar la plataforma sin administradores). */
    UsuarioAdmin cambiarRoles(Long idUsuario, Long idAdmin, List<String> roles);

    /** Activa, desactiva o bloquea la cuenta (no puede dejar la plataforma sin administradores). */
    UsuarioAdmin cambiarEstado(Long idUsuario, Long idAdmin, String estado);

    /** Promociones del catalogo, opcionalmente filtradas por estado. */
    List<PromocionAdmin> promociones(String estado, int limite);

    /** Crea una promocion y la asocia a las canchas indicadas (opcional). */
    PromocionAdmin crearPromocion(Long idAdmin, String codigo, String descripcion, String tipo,
                                  BigDecimal valor, LocalDate fechaInicio, LocalDate fechaFin,
                                  List<Long> canchas);

    /** Activa o desactiva una promocion. */
    PromocionAdmin cambiarEstadoPromocion(Long idPromocion, Long idAdmin, String estado);

    /** Cocheras de todos los establecimientos. */
    List<CocheraAdmin> cocheras(String estado, int limite);

    /** Activa o desactiva una cochera. */
    CocheraAdmin cambiarEstadoCochera(Long idCochera, Long idAdmin, String estado);
}
