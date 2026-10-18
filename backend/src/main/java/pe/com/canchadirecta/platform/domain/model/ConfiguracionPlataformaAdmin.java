package pe.com.canchadirecta.platform.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

/** Configuracion persistente visible y editable por PLATFORM_ADMIN (ADR-0037). */
public record ConfiguracionPlataformaAdmin(
        BigDecimal comisionPorcentaje,
        long horasCancelacionGratis,
        long horasMinimasCancelacion,
        long diasAnticipacionMax,
        String correoSoporte,
        Instant fechaActualizacion) {
}
