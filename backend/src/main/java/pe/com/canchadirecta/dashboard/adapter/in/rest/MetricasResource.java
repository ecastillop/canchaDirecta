package pe.com.canchadirecta.dashboard.adapter.in.rest;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import pe.com.canchadirecta.dashboard.application.port.in.MetricasUseCase;
import pe.com.canchadirecta.dashboard.domain.model.MetricasEstablecimiento;

/** Metricas del establecimiento para el panel del propietario. */
@Path("/api/v1/establecimientos/{idEstablecimiento}/metricas")
@Authenticated
@Tag(name = "dashboard", description = "Metricas de operacion del establecimiento")
public class MetricasResource {

    private final MetricasUseCase metricas;
    private final JsonWebToken jwt;

    @Inject
    public MetricasResource(MetricasUseCase metricas, JsonWebToken jwt) {
        this.metricas = metricas;
        this.jwt = jwt;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Resumen, serie diaria, canchas top y proximas reservas del establecimiento")
    public MetricasEstablecimiento metricas(@PathParam("idEstablecimiento") Long idEstablecimiento,
                                            @QueryParam("dias") @DefaultValue("30") int dias) {
        return metricas.delEstablecimiento(Long.valueOf(jwt.getSubject()), idEstablecimiento, dias);
    }
}
