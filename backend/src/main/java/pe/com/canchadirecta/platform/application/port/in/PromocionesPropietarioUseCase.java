package pe.com.canchadirecta.platform.application.port.in;

import pe.com.canchadirecta.platform.domain.model.PromocionAdmin;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Promociones de un establecimiento gestionadas por su propietario (CU04.6). Se asocian a todas
 * las canchas del establecimiento en el momento del alta.
 */
public interface PromocionesPropietarioUseCase {

    List<PromocionAdmin> listar(Long idEstablecimiento, Long idUsuario, int limite);

    PromocionAdmin crear(Long idEstablecimiento, Long idUsuario, String codigo, String descripcion,
                         String tipo, BigDecimal valor, LocalDate fechaInicio, LocalDate fechaFin,
                         Integer limiteUsos, Integer limitePorUsuario);

    PromocionAdmin cambiarEstado(Long idEstablecimiento, Long idPromocion, Long idUsuario, String estado);
}
