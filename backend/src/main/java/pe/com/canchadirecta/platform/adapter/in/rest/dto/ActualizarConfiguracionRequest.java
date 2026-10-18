package pe.com.canchadirecta.platform.adapter.in.rest.dto;

import java.math.BigDecimal;

/** Reemplazo completo de la configuracion de plataforma (PUT /api/v1/admin/configuracion). */
public record ActualizarConfiguracionRequest(
        BigDecimal comisionPorcentaje,
        Long horasCancelacionGratis,
        Long horasMinimasCancelacion,
        Long diasAnticipacionMax,
        String correoSoporte) {
}
