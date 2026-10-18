package pe.com.canchadirecta.dashboard.application.port.in;

import pe.com.canchadirecta.dashboard.domain.model.MetricasEstablecimiento;

/** Metricas del panel del propietario. */
public interface MetricasUseCase {

    MetricasEstablecimiento delEstablecimiento(Long idUsuario, Long idEstablecimiento, int dias);
}
