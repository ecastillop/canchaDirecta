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
import pe.com.canchadirecta.platform.adapter.in.rest.dto.AdminSupervisionResponse;
import pe.com.canchadirecta.platform.application.port.in.AdminPlataformaUseCase;

import java.util.List;

/**
 * Supervision de pagos y reembolsos (CU06.4).
 */
@Path("/api/v1/admin/pagos")
@RolesAllowed("PLATFORM_ADMIN")
@Tag(name = "admin", description = "Administracion de plataforma (PLATFORM_ADMIN)")
public class AdminPagoResource {

    private final AdminPlataformaUseCase admin;

    @Inject
    public AdminPagoResource(AdminPlataformaUseCase admin) {
        this.admin = admin;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Pagos con el total reembolsado acumulado")
    public List<AdminSupervisionResponse.Pago> listar(@QueryParam("estado") String estado,
                                                      @QueryParam("limite") @DefaultValue("50") int limite) {
        return admin.pagos(estado, limite).stream().map(AdminSupervisionResponse.Pago::de).toList();
    }
}
