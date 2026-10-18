package pe.com.canchadirecta.dashboard.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import pe.com.canchadirecta.dashboard.application.port.in.MetricasUseCase;
import pe.com.canchadirecta.dashboard.application.port.out.MetricasRepositorio;
import pe.com.canchadirecta.dashboard.domain.model.MetricasEstablecimiento;
import pe.com.canchadirecta.shared.application.port.out.Reloj;
import pe.com.canchadirecta.shared.domain.DomainException;
import pe.com.canchadirecta.shared.domain.ErrorCodes;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Metricas del establecimiento para el propietario. La ventana se calcula en dias de negocio
 * (America/Lima) y termina al final del dia actual.
 */
@ApplicationScoped
public class MetricasService implements MetricasUseCase {

    private static final ZoneId ZONA_NEGOCIO = ZoneId.of("America/Lima");

    private final MetricasRepositorio repositorio;
    private final Reloj reloj;

    @Inject
    public MetricasService(MetricasRepositorio repositorio, Reloj reloj) {
        this.repositorio = repositorio;
        this.reloj = reloj;
    }

    @Override
    public MetricasEstablecimiento delEstablecimiento(Long idUsuario, Long idEstablecimiento, int dias) {
        if (idEstablecimiento == null) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "El establecimiento es obligatorio.");
        }
        if (!repositorio.esPropietarioDe(idEstablecimiento, idUsuario)) {
            throw new DomainException(ErrorCodes.SIN_PERMISO,
                    "Solo el propietario puede consultar las metricas de este establecimiento.");
        }
        int ventana = Math.max(1, Math.min(dias <= 0 ? 30 : dias, 365));
        Instant ahora = reloj.ahora();
        LocalDate hoy = LocalDate.ofInstant(ahora, ZONA_NEGOCIO);
        Instant desde = hoy.minusDays(ventana - 1L).atStartOfDay(ZONA_NEGOCIO).toInstant();
        Instant hasta = hoy.plusDays(1).atStartOfDay(ZONA_NEGOCIO).toInstant();
        return repositorio.metricas(idEstablecimiento, desde, hasta, ahora, ventana)
                .orElseThrow(() -> new DomainException(ErrorCodes.NO_ENCONTRADO,
                        "El establecimiento no existe."));
    }
}
