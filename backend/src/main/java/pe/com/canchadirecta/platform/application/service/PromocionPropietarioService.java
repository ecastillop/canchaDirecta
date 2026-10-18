package pe.com.canchadirecta.platform.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import pe.com.canchadirecta.platform.application.port.in.AdminPlataformaUseCase;
import pe.com.canchadirecta.platform.application.port.in.PromocionesPropietarioUseCase;
import pe.com.canchadirecta.platform.application.port.out.AdminPlataformaRepositorio;
import pe.com.canchadirecta.platform.domain.model.PromocionAdmin;
import pe.com.canchadirecta.shared.domain.DomainException;
import pe.com.canchadirecta.shared.domain.ErrorCodes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Promociones del propietario (CU04.6). La pertenencia se resuelve contra `propietario.id_usuario`
 * y el alta se asocia a todas las canchas del establecimiento; la validacion de datos y la
 * auditoria se reutilizan del servicio de administracion (ADR-0029).
 */
@ApplicationScoped
public class PromocionPropietarioService implements PromocionesPropietarioUseCase {

    private static final int LIMITE_DEFECTO = 50;

    private final AdminPlataformaRepositorio admin;
    private final AdminPlataformaUseCase catalogo;

    @Inject
    public PromocionPropietarioService(AdminPlataformaRepositorio admin, AdminPlataformaUseCase catalogo) {
        this.admin = admin;
        this.catalogo = catalogo;
    }

    @Override
    public List<PromocionAdmin> listar(Long idEstablecimiento, Long idUsuario, int limite) {
        exigirPropietario(idEstablecimiento, idUsuario);
        return admin.promocionesDeEstablecimiento(idEstablecimiento,
                limite > 0 ? limite : LIMITE_DEFECTO);
    }

    @Override
    public PromocionAdmin crear(Long idEstablecimiento, Long idUsuario, String codigo, String descripcion,
                                String tipo, BigDecimal valor, LocalDate fechaInicio, LocalDate fechaFin,
                                Integer limiteUsos, Integer limitePorUsuario) {
        exigirPropietario(idEstablecimiento, idUsuario);
        List<Long> canchas = admin.canchasDeEstablecimiento(idEstablecimiento);
        if (canchas.isEmpty()) {
            throw new DomainException(ErrorCodes.CONFLICTO,
                    "El establecimiento no tiene canchas para asociar la promocion.");
        }
        return catalogo.crearPromocion(idUsuario, codigo, descripcion, tipo, valor, fechaInicio, fechaFin,
                limiteUsos, limitePorUsuario, canchas);
    }

    @Override
    public PromocionAdmin cambiarEstado(Long idEstablecimiento, Long idPromocion, Long idUsuario,
                                        String estado) {
        exigirPropietario(idEstablecimiento, idUsuario);
        if (!admin.promocionDeEstablecimiento(idPromocion, idEstablecimiento)) {
            throw new DomainException(ErrorCodes.NO_ENCONTRADO,
                    "La promocion no pertenece a una cancha del establecimiento.");
        }
        return catalogo.cambiarEstadoPromocion(idPromocion, idUsuario, estado);
    }

    private void exigirPropietario(Long idEstablecimiento, Long idUsuario) {
        if (idEstablecimiento == null) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "El establecimiento es obligatorio.");
        }
        if (!admin.esPropietarioDeEstablecimiento(idEstablecimiento, idUsuario)) {
            throw new DomainException(ErrorCodes.SIN_PERMISO, "El establecimiento pertenece a otro propietario.");
        }
    }
}
