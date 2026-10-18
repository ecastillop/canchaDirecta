package pe.com.canchadirecta.platform.adapter.in.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import pe.com.canchadirecta.platform.adapter.in.rest.dto.AdminCocheraResponse;
import pe.com.canchadirecta.platform.adapter.in.rest.dto.CambiarEstadoSimpleRequest;
import pe.com.canchadirecta.platform.application.port.in.AdminPlataformaUseCase;

import java.util.List;

/**
 * Supervision y estado de cocheras por la administracion (CU06.5).
 */
@Path("/api/v1/admin/cocheras")
@RolesAllowed("PLATFORM_ADMIN")
@Tag(name = "admin", description = "Administracion de plataforma (PLATFORM_ADMIN)")
public class AdminCocheraResource {

    private final AdminPlataformaUseCase admin;
    private final JsonWebToken jwt;

    @Inject
    public AdminCocheraResource(AdminPlataformaUseCase admin, JsonWebToken jwt) {
        this.admin = admin;
        this.jwt = jwt;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Cocheras de todos los establecimientos (filtro opcional por estado)")
    public List<AdminCocheraResponse> listar(@QueryParam("estado") String estado,
                                             @QueryParam("limite") @DefaultValue("50") int limite) {
        return admin.cocheras(estado, limite).stream().map(AdminCocheraResponse::de).toList();
    }

    @POST
    @Path("/{idCochera}/estado")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Activa o desactiva una cochera (409 si ya esta en ese estado)")
    public AdminCocheraResponse cambiarEstado(@PathParam("idCochera") Long idCochera,
                                              @Valid CambiarEstadoSimpleRequest request) {
        return AdminCocheraResponse.de(admin.cambiarEstadoCochera(idCochera,
                Long.valueOf(jwt.getSubject()), request.estado()));
    }
}
