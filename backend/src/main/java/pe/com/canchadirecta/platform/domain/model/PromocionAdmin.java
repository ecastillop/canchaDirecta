package pe.com.canchadirecta.platform.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Promocion del catalogo administrada por plataforma (CU06.5), opcionalmente asociada a canchas
 * (`cancha_promocion`).
 */
public record PromocionAdmin(
        Long id,
        String codigo,
        String descripcion,
        String tipoDescuento,
        BigDecimal valor,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        String estado,
        Integer limiteUsos,
        Integer limitePorUsuario,
        long usos,
        List<Long> canchas
) {

    public static final int CODIGO_MAX = 30;
    public static final int DESCRIPCION_MAX = 200;

    /** Estados admitidos por `ck_promocion_estado` (V1). */
    public enum Estado {
        ACTIVA, INACTIVA
    }

    /** Tipos admitidos por `ck_promocion_tipo` (V1). */
    public enum Tipo {
        PORCENTAJE, MONTO
    }
}
