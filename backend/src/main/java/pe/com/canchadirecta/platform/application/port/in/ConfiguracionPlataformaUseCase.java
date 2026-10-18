package pe.com.canchadirecta.platform.application.port.in;

import pe.com.canchadirecta.platform.domain.model.ConfiguracionPlataformaAdmin;

import java.math.BigDecimal;

/** Casos de uso de la configuracion persistente de la plataforma (ADR-0037). */
public interface ConfiguracionPlataformaUseCase {

    ConfiguracionPlataformaAdmin consultar();

    ConfiguracionPlataformaAdmin actualizar(Long idAdmin, BigDecimal comisionPorcentaje,
                                            Long horasCancelacionGratis, Long horasMinimasCancelacion,
                                            Long diasAnticipacionMax, String correoSoporte);
}
