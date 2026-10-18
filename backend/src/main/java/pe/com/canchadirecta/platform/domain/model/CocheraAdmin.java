package pe.com.canchadirecta.platform.domain.model;

import java.math.BigDecimal;

/**
 * Cochera vista por la administracion (CU06.5), con su establecimiento.
 */
public record CocheraAdmin(
        Long id,
        Long idEstablecimiento,
        String establecimiento,
        String nombre,
        int capacidad,
        BigDecimal tarifaHora,
        String estado
) {

    /** Estados admitidos por `ck_cochera_estado` (V1). */
    public enum Estado {
        ACTIVA, INACTIVA
    }
}
