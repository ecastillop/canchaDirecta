package pe.com.canchadirecta.platform.adapter.in.rest.dto;

import pe.com.canchadirecta.platform.domain.model.PromocionAdmin;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Promocion del catalogo con las canchas asociadas.
 */
public record AdminPromocionResponse(
        Long id,
        String codigo,
        String descripcion,
        String tipoDescuento,
        BigDecimal valor,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        String estado,
        List<Long> canchas
) {

    public static AdminPromocionResponse de(PromocionAdmin promocion) {
        return new AdminPromocionResponse(promocion.id(), promocion.codigo(),
                promocion.descripcion(), promocion.tipoDescuento(), promocion.valor(),
                promocion.fechaInicio(), promocion.fechaFin(), promocion.estado(),
                promocion.canchas());
    }
}
