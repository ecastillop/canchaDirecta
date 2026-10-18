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
import pe.com.canchadirecta.platform.adapter.in.rest.dto.AdminSupervisionResponse;
import pe.com.canchadirecta.platform.adapter.in.rest.dto.CambiarEstadoSimpleRequest;
import pe.com.canchadirecta.platform.application.port.in.AdminPlataformaUseCase;

import java.util.List;

/**
 * Supervision de establecimientos (CU06.3).
 */
@Path("/api/v1/admin/establecimientos")
@RolesAllowed("PLATFORM_ADMIN")
@Tag(name = "admin", description = "Administracion de plataforma (PLATFORM_ADMIN)")
public class AdminEstablecimientoResource {

    private final AdminPlataformaUseCase admin;
    private final JsonWebToken jwt;

    @Inject
    public AdminEstablecimientoResource(AdminPlataformaUseCase admin, JsonWebToken jwt) {
        this.admin = admin;
        this.jwt = jwt;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Establecimientos con propietario, distrito y numero de canchas")
    public List<AdminSupervisionResponse.Establecimiento> listar(
            @QueryParam("estado") String estado,
            @QueryParam("limite") @DefaultValue("50") int limite) {
        return admin.establecimientos(estado, limite).stream()
                .map(AdminSupervisionResponse.Establecimiento::de).toList();
    }

    @POST
    @Path("/{idEstablecimiento}/estado")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Activa o desactiva un establecimiento (409 si ya esta en ese estado)")
    public AdminSupervisionResponse.Establecimiento cambiarEstado(
            @PathParam("idEstablecimiento") Long idEstablecimiento,
            @Valid CambiarEstadoSimpleRequest request) {
        return AdminSupervisionResponse.Establecimiento.de(
                admin.cambiarEstadoEstablecimiento(idEstablecimiento,
                        Long.valueOf(jwt.getSubject()), request.estado()));
    }
}
