package pe.com.canchadirecta.platform.adapter.in.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import pe.com.canchadirecta.platform.application.port.in.AdminPlataformaUseCase;
import pe.com.canchadirecta.platform.domain.model.ReporteAdmin;

/** Reportes agregados de plataforma para la administracion. */
@Path("/api/v1/admin/reportes")
@RolesAllowed("PLATFORM_ADMIN")
@Tag(name = "admin", description = "Administracion de plataforma (PLATFORM_ADMIN)")
public class AdminReporteResource {

    private final AdminPlataformaUseCase admin;

    @Inject
    public AdminReporteResource(AdminPlataformaUseCase admin) {
        this.admin = admin;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Resumen, series, estados y establecimientos top de la plataforma")
    public ReporteAdmin reporte(@QueryParam("dias") @DefaultValue("30") int dias) {
        return admin.reporte(dias);
    }
}
