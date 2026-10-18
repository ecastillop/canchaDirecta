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
import pe.com.canchadirecta.platform.adapter.in.rest.dto.AuditoriaResponse;
import pe.com.canchadirecta.platform.application.port.in.AdminPlataformaUseCase;

import java.util.List;

/**
 * Consulta del registro de auditoria (CU06.7).
 */
@Path("/api/v1/admin/auditoria")
@RolesAllowed("PLATFORM_ADMIN")
@Tag(name = "admin", description = "Administracion de plataforma (PLATFORM_ADMIN)")
public class AdminAuditoriaResource {

    private final AdminPlataformaUseCase admin;

    @Inject
    public AdminAuditoriaResource(AdminPlataformaUseCase admin) {
        this.admin = admin;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Ultimas acciones administrativas (mas recientes primero)")
    public List<AuditoriaResponse> listar(@QueryParam("limite") @DefaultValue("100") int limite) {
        return admin.auditoria(limite).stream().map(AuditoriaResponse::de).toList();
    }
}
