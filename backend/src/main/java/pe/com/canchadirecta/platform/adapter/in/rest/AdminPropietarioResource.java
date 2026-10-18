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
import pe.com.canchadirecta.platform.adapter.in.rest.dto.AdminPropietarioResponse;
import pe.com.canchadirecta.platform.adapter.in.rest.dto.CambiarValidacionRequest;
import pe.com.canchadirecta.platform.application.port.in.AdminPlataformaUseCase;

import java.util.List;

/**
 * Validacion de propietarios por la administracion (CU06.2).
 */
@Path("/api/v1/admin/propietarios")
@RolesAllowed("PLATFORM_ADMIN")
@Tag(name = "admin", description = "Administracion de plataforma (PLATFORM_ADMIN)")
public class AdminPropietarioResource {

    private final AdminPlataformaUseCase admin;
    private final JsonWebToken jwt;

    @Inject
    public AdminPropietarioResource(AdminPlataformaUseCase admin, JsonWebToken jwt) {
        this.admin = admin;
        this.jwt = jwt;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Propietarios con su estado de validacion (filtro opcional)")
    public List<AdminPropietarioResponse> listar(@QueryParam("estado") String estado,
                                                 @QueryParam("limite") @DefaultValue("50") int limite) {
        return admin.propietarios(estado, limite).stream().map(AdminPropietarioResponse::de).toList();
    }

    @POST
    @Path("/{idPropietario}/validacion")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Valida o rechaza un propietario, avisa al usuario y audita (409 si no procede)")
    public AdminPropietarioResponse validar(@PathParam("idPropietario") Long idPropietario,
                                            @Valid CambiarValidacionRequest request) {
        return AdminPropietarioResponse.de(admin.cambiarValidacion(idPropietario,
                Long.valueOf(jwt.getSubject()), request.estado(), request.motivo()));
    }
}
