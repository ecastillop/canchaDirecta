package pe.com.canchadirecta.dashboard.application.port.out;

import pe.com.canchadirecta.dashboard.domain.model.MetricasEstablecimiento;

import java.time.Instant;
import java.util.Optional;

/** Consultas agregadas de reservas e ingresos por establecimiento. */
public interface MetricasRepositorio {

    boolean esPropietarioDe(Long idEstablecimiento, Long idUsuario);

    Optional<MetricasEstablecimiento> metricas(Long idEstablecimiento, Instant desde, Instant hasta,
                                               Instant ahora, int dias);
}
