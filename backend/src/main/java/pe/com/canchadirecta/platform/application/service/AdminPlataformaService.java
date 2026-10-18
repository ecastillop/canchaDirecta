package pe.com.canchadirecta.platform.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.com.canchadirecta.platform.application.port.in.AdminPlataformaUseCase;
import pe.com.canchadirecta.platform.application.port.out.AdminPlataformaRepositorio;
import pe.com.canchadirecta.platform.domain.model.Auditoria;
import pe.com.canchadirecta.platform.domain.model.CocheraAdmin;
import pe.com.canchadirecta.platform.domain.model.PromocionAdmin;
import pe.com.canchadirecta.platform.domain.model.PropietarioAdmin;
import pe.com.canchadirecta.platform.domain.model.SupervisionAdmin;
import pe.com.canchadirecta.platform.domain.model.UsuarioAdmin;
import pe.com.canchadirecta.shared.application.port.out.AvisosUsuario;
import pe.com.canchadirecta.shared.application.port.out.RegistroAuditoria;
import pe.com.canchadirecta.shared.domain.DomainException;
import pe.com.canchadirecta.shared.domain.ErrorCodes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Administracion de plataforma (F07): validacion de propietarios (CU06.2), supervision de solo
 * lectura (CU06.3/CU06.4) y auditoria (CU06.7).
 *
 * <p>La validacion es condicional al estado leido (`409` si otra accion gano la carrera), avisa al
 * propietario y deja una entrada en `auditoria`. Rechazar exige motivo.</p>
 */
@ApplicationScoped
public class AdminPlataformaService implements AdminPlataformaUseCase {

    private static final int LIMITE_DEFECTO = 50;

    private static final Set<String> ROLES = Set.of("USER", "OWNER", "PLATFORM_ADMIN");
    private static final String ADMIN = "PLATFORM_ADMIN";

    private static final Map<PropietarioAdmin.Estado, Set<PropietarioAdmin.Estado>> TRANSICIONES = Map.of(
            PropietarioAdmin.Estado.PENDIENTE,
            EnumSet.of(PropietarioAdmin.Estado.VALIDADO, PropietarioAdmin.Estado.RECHAZADO),
            PropietarioAdmin.Estado.RECHAZADO,
            EnumSet.of(PropietarioAdmin.Estado.VALIDADO),
            PropietarioAdmin.Estado.VALIDADO,
            EnumSet.of(PropietarioAdmin.Estado.RECHAZADO));

    private final AdminPlataformaRepositorio admin;
    private final AvisosUsuario avisos;
    private final RegistroAuditoria auditoria;

    @Inject
    public AdminPlataformaService(AdminPlataformaRepositorio admin, AvisosUsuario avisos,
                                  RegistroAuditoria auditoria) {
        this.admin = admin;
        this.avisos = avisos;
        this.auditoria = auditoria;
    }

    @Override
    public List<PropietarioAdmin> propietarios(String estado, int limite) {
        return admin.propietarios(estadoFiltro(estado), limite(limite));
    }

    @Override
    @Transactional
    public PropietarioAdmin cambiarValidacion(Long idPropietario, Long idAdmin, String estado, String motivo) {
        PropietarioAdmin actual = admin.propietario(idPropietario)
                .orElseThrow(() -> new DomainException(ErrorCodes.NO_ENCONTRADO, "El propietario no existe."));
        PropietarioAdmin.Estado nuevo = estadoFiltro(estado) == null
                ? null
                : PropietarioAdmin.Estado.valueOf(estadoFiltro(estado));
        if (nuevo == null) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "El estado es obligatorio.");
        }
        if (!TRANSICIONES.getOrDefault(actual.estadoValidacion(), Set.of()).contains(nuevo)) {
            throw new DomainException(ErrorCodes.CONFLICTO,
                    "No se puede pasar de " + actual.estadoValidacion() + " a " + nuevo + ".");
        }
        if (nuevo == PropietarioAdmin.Estado.RECHAZADO && (motivo == null || motivo.trim().isEmpty())) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "El motivo es obligatorio al rechazar.");
        }
        if (!admin.cambiarValidacion(actual.id(), actual.estadoValidacion().name(), nuevo.name())) {
            throw new DomainException(ErrorCodes.CONFLICTO, "El propietario cambio mientras se validaba.");
        }

        String detalle = "documento=" + actual.documento() + " estado=" + nuevo
                + (motivo == null || motivo.isBlank() ? "" : " motivo=" + motivo.trim());
        auditoria.registrar(idAdmin, "PROPIETARIO_" + nuevo.name(), "propietario",
                String.valueOf(actual.id()), detalle);
        if (nuevo == PropietarioAdmin.Estado.VALIDADO) {
            avisos.avisar(actual.idUsuario(), "Propietario validado",
                    "Tu cuenta de propietario fue validada; ya puedes publicar canchas.");
        } else {
            avisos.avisar(actual.idUsuario(), "Validacion rechazada",
                    "Tu solicitud de propietario fue rechazada. Motivo: " + motivo.trim());
        }
        return new PropietarioAdmin(actual.id(), actual.idUsuario(), actual.nombreCompleto(),
                actual.correo(), actual.documento(), nuevo);
    }

    @Override
    public List<SupervisionAdmin.Establecimiento> establecimientos(String estado, int limite) {
        return admin.establecimientos(texto(estado), limite(limite));
    }

    @Override
    public List<SupervisionAdmin.Reserva> reservas(String estado, int limite) {
        return admin.reservas(texto(estado), limite(limite));
    }

    @Override
    public List<SupervisionAdmin.Pago> pagos(String estado, int limite) {
        return admin.pagos(texto(estado), limite(limite));
    }

    @Override
    public List<Auditoria> auditoria(int limite) {
        return admin.auditoria(limite(limite));
    }

    @Override
    public List<UsuarioAdmin> usuarios(String correo, String estado, String rol, int limite) {
        return admin.usuarios(textoLibre(correo), estadoUsuario(estado), rolDe(rol), limite(limite));
    }

    @Override
    public UsuarioAdmin usuario(Long idUsuario) {
        return admin.usuario(idUsuario)
                .orElseThrow(() -> new DomainException(ErrorCodes.NO_ENCONTRADO, "El usuario no existe."));
    }

    @Override
    @Transactional
    public UsuarioAdmin cambiarRoles(Long idUsuario, Long idAdmin, List<String> roles) {
        UsuarioAdmin actual = usuario(idUsuario);
        Set<String> nuevos = normalizarRoles(roles);
        boolean eraAdmin = actual.roles().contains(ADMIN);
        if (eraAdmin && !nuevos.contains(ADMIN) && admin.contarOtrosAdminsActivos(actual.id()) == 0) {
            throw new DomainException(ErrorCodes.CONFLICTO,
                    "No se puede dejar la plataforma sin administradores.");
        }
        admin.reemplazarRoles(actual.id(), nuevos);
        admin.invalidarTokensDeUsuario(actual.id());
        auditoria.registrar(idAdmin, "USUARIO_ROLES_CAMBIADOS", "usuario", String.valueOf(actual.id()),
                "antes=" + actual.roles() + " despues=" + nuevos);
        avisos.avisar(actual.id(), "Tus roles cambiaron",
                "Ahora tienes: " + String.join(", ", nuevos) + ". Vuelve a iniciar sesion.");
        return usuario(actual.id());
    }

    @Override
    @Transactional
    public UsuarioAdmin cambiarEstado(Long idUsuario, Long idAdmin, String estado) {
        UsuarioAdmin actual = usuario(idUsuario);
        UsuarioAdmin.Estado nuevo = estadoUsuario(estado) == null
                ? null
                : UsuarioAdmin.Estado.valueOf(estadoUsuario(estado));
        if (nuevo == null) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "El estado es obligatorio.");
        }
        if (actual.estado().equals(nuevo.name())) {
            throw new DomainException(ErrorCodes.CONFLICTO, "El usuario ya esta en ese estado.");
        }
        if (actual.roles().contains(ADMIN) && nuevo != UsuarioAdmin.Estado.ACTIVO
                && admin.contarOtrosAdminsActivos(actual.id()) == 0) {
            throw new DomainException(ErrorCodes.CONFLICTO,
                    "No se puede desactivar al ultimo administrador activo.");
        }
        if (!admin.cambiarEstado(actual.id(), actual.estado(), nuevo.name())) {
            throw new DomainException(ErrorCodes.CONFLICTO, "El usuario cambio mientras se actualizaba.");
        }
        admin.invalidarTokensDeUsuario(actual.id());
        auditoria.registrar(idAdmin, "USUARIO_ESTADO_CAMBIADO", "usuario", String.valueOf(actual.id()),
                "de=" + actual.estado() + " a=" + nuevo);
        avisos.avisar(actual.id(), nuevo == UsuarioAdmin.Estado.ACTIVO ? "Cuenta activada" : "Cuenta bloqueada",
                nuevo == UsuarioAdmin.Estado.ACTIVO
                        ? "Tu cuenta volvio a estar activa."
                        : "Tu cuenta paso a " + nuevo + ".");
        return usuario(actual.id());
    }

    @Override
    public List<PromocionAdmin> promociones(String estado, int limite) {
        return admin.promociones(estadoPromocion(estado), limite(limite));
    }

    @Override
    @Transactional
    public PromocionAdmin crearPromocion(Long idAdmin, String codigo, String descripcion, String tipo,
                                         BigDecimal valor, LocalDate fechaInicio, LocalDate fechaFin,
                                         List<Long> canchas) {
        String codigoLimpio = codigo == null ? "" : codigo.trim().toUpperCase(Locale.ROOT);
        if (!codigoLimpio.matches("[A-Z0-9_-]{1," + PromocionAdmin.CODIGO_MAX + "}")) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS,
                    "El codigo debe tener 1..30 caracteres (letras, numeros, '-' o '_').");
        }
        if (descripcion == null || descripcion.isBlank()
                || descripcion.trim().length() > PromocionAdmin.DESCRIPCION_MAX) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS,
                    "La descripcion es obligatoria y no puede superar 200 caracteres.");
        }
        PromocionAdmin.Tipo tipoValor = tipoPromocion(tipo);
        if (valor == null || valor.signum() <= 0) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "El valor debe ser mayor que cero.");
        }
        if (tipoValor == PromocionAdmin.Tipo.PORCENTAJE
                && valor.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "Un porcentaje no puede superar 100.");
        }
        if (fechaInicio == null || fechaFin == null) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "Las fechas de la promocion son obligatorias.");
        }
        if (fechaInicio.isAfter(fechaFin)) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS,
                    "La fecha de inicio no puede ser posterior a la de fin.");
        }
        List<Long> canchasLimpio = canchas == null ? List.of()
                : canchas.stream().filter(Objects::nonNull).distinct().toList();
        if (admin.codigoPromocionExiste(codigoLimpio)) {
            throw new DomainException(ErrorCodes.CONFLICTO, "Ya existe una promocion con ese codigo.");
        }
        if (!canchasLimpio.isEmpty() && admin.contarCanchas(canchasLimpio) != canchasLimpio.size()) {
            throw new DomainException(ErrorCodes.NO_ENCONTRADO, "Alguna cancha de la promocion no existe.");
        }

        PromocionAdmin creada = admin.registrarPromocion(new PromocionAdmin(null, codigoLimpio,
                descripcion.trim(), tipoValor.name(), valor, fechaInicio, fechaFin,
                PromocionAdmin.Estado.ACTIVA.name(), canchasLimpio));
        auditoria.registrar(idAdmin, "PROMOCION_CREADA", "promocion", String.valueOf(creada.id()),
                "codigo=" + codigoLimpio + " tipo=" + tipoValor + " valor=" + valor
                        + " canchas=" + canchasLimpio.size());
        return creada;
    }

    @Override
    @Transactional
    public PromocionAdmin cambiarEstadoPromocion(Long idPromocion, Long idAdmin, String estado) {
        PromocionAdmin actual = admin.promocion(idPromocion)
                .orElseThrow(() -> new DomainException(ErrorCodes.NO_ENCONTRADO, "La promocion no existe."));
        String nuevo = estadoPromocion(estado);
        if (nuevo == null) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "El estado es obligatorio.");
        }
        if (actual.estado().equals(nuevo)) {
            throw new DomainException(ErrorCodes.CONFLICTO, "La promocion ya esta en ese estado.");
        }
        if (!admin.cambiarEstadoPromocion(actual.id(), actual.estado(), nuevo)) {
            throw new DomainException(ErrorCodes.CONFLICTO, "La promocion cambio mientras se actualizaba.");
        }
        auditoria.registrar(idAdmin, "PROMOCION_" + nuevo, "promocion", String.valueOf(actual.id()),
                "codigo=" + actual.codigo() + " de=" + actual.estado() + " a=" + nuevo);
        return admin.promocion(actual.id()).orElseThrow();
    }

    @Override
    public List<CocheraAdmin> cocheras(String estado, int limite) {
        return admin.cocheras(estadoCochera(estado), limite(limite));
    }

    @Override
    @Transactional
    public CocheraAdmin cambiarEstadoCochera(Long idCochera, Long idAdmin, String estado) {
        CocheraAdmin actual = admin.cochera(idCochera)
                .orElseThrow(() -> new DomainException(ErrorCodes.NO_ENCONTRADO, "La cochera no existe."));
        String nuevo = estadoCochera(estado);
        if (nuevo == null) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "El estado es obligatorio.");
        }
        if (actual.estado().equals(nuevo)) {
            throw new DomainException(ErrorCodes.CONFLICTO, "La cochera ya esta en ese estado.");
        }
        if (!admin.cambiarEstadoCochera(actual.id(), actual.estado(), nuevo)) {
            throw new DomainException(ErrorCodes.CONFLICTO, "La cochera cambio mientras se actualizaba.");
        }
        auditoria.registrar(idAdmin, "COCHERA_" + nuevo, "cochera", String.valueOf(actual.id()),
                "establecimiento=" + actual.idEstablecimiento() + " de=" + actual.estado() + " a=" + nuevo);
        return admin.cochera(actual.id()).orElseThrow();
    }

    private static PromocionAdmin.Tipo tipoPromocion(String tipo) {
        String valor = tipo == null ? "" : tipo.trim().toUpperCase(Locale.ROOT);
        for (PromocionAdmin.Tipo candidato : PromocionAdmin.Tipo.values()) {
            if (candidato.name().equals(valor)) {
                return candidato;
            }
        }
        throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "Tipo de descuento desconocido: " + tipo + ".");
    }

    private static String estadoPromocion(String estado) {
        if (estado == null || estado.isBlank()) {
            return null;
        }
        for (PromocionAdmin.Estado candidato : PromocionAdmin.Estado.values()) {
            if (candidato.name().equalsIgnoreCase(estado.trim())) {
                return candidato.name();
            }
        }
        throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "Estado de promocion desconocido: " + estado + ".");
    }

    private static String estadoCochera(String estado) {
        if (estado == null || estado.isBlank()) {
            return null;
        }
        for (CocheraAdmin.Estado candidato : CocheraAdmin.Estado.values()) {
            if (candidato.name().equalsIgnoreCase(estado.trim())) {
                return candidato.name();
            }
        }
        throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "Estado de cochera desconocido: " + estado + ".");
    }

    private static Set<String> normalizarRoles(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "Debe indicar al menos un rol.");
        }
        Set<String> limpios = new LinkedHashSet<>();
        for (String rol : roles) {
            String valor = rol == null ? "" : rol.trim().toUpperCase(Locale.ROOT);
            if (!ROLES.contains(valor)) {
                throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "Rol desconocido: " + rol + ".");
            }
            limpios.add(valor);
        }
        return limpios;
    }

    private static String estadoUsuario(String estado) {
        if (estado == null || estado.isBlank()) {
            return null;
        }
        for (UsuarioAdmin.Estado candidato : UsuarioAdmin.Estado.values()) {
            if (candidato.name().equalsIgnoreCase(estado.trim())) {
                return candidato.name();
            }
        }
        throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "Estado de usuario desconocido: " + estado + ".");
    }

    private static String rolDe(String rol) {
        if (rol == null || rol.isBlank()) {
            return null;
        }
        String valor = rol.trim().toUpperCase(Locale.ROOT);
        if (!ROLES.contains(valor)) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "Rol desconocido: " + rol + ".");
        }
        return valor;
    }

    private static String textoLibre(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private static int limite(int pedido) {
        return pedido > 0 ? pedido : LIMITE_DEFECTO;
    }

    private static String texto(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim().toUpperCase();
    }

    /** `null`/vacio = sin filtro; un valor desconocido es 400. */
    private static String estadoFiltro(String estado) {
        if (estado == null || estado.isBlank()) {
            return null;
        }
        PropietarioAdmin.Estado valor = PropietarioAdmin.Estado.de(estado);
        if (valor == null) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS,
                    "Estado de propietario desconocido: " + estado + ".");
        }
        return valor.name();
    }
}
