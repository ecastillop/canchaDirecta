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
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import pe.com.canchadirecta.platform.adapter.in.rest.dto.AdminPromocionResponse;
import pe.com.canchadirecta.platform.adapter.in.rest.dto.CambiarEstadoSimpleRequest;
import pe.com.canchadirecta.platform.adapter.in.rest.dto.CrearPromocionRequest;
import pe.com.canchadirecta.platform.application.port.in.AdminPlataformaUseCase;

import java.util.List;

/**
 * Catalogo de promociones administrado por plataforma (CU06.5).
 */
@Path("/api/v1/admin/promociones")
@RolesAllowed("PLATFORM_ADMIN")
@Tag(name = "admin", description = "Administracion de plataforma (PLATFORM_ADMIN)")
public class AdminPromocionResource {

    private final AdminPlataformaUseCase admin;
    private final JsonWebToken jwt;

    @Inject
    public AdminPromocionResource(AdminPlataformaUseCase admin, JsonWebToken jwt) {
        this.admin = admin;
        this.jwt = jwt;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Promociones del catalogo, con filtro opcional por estado")
    public List<AdminPromocionResponse> listar(@QueryParam("estado") String estado,
                                               @QueryParam("limite") @DefaultValue("50") int limite) {
        return admin.promociones(estado, limite).stream().map(AdminPromocionResponse::de).toList();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Crea una promocion y la asocia a canchas (codigo unico, 409 si ya existe)")
    public Response crear(@Valid CrearPromocionRequest request) {
        var promocion = admin.crearPromocion(idAdministrador(), request.codigo(),
                request.descripcion(), request.tipoDescuento(), request.valor(),
                request.fechaInicio(), request.fechaFin(), request.limiteUsos(),
                request.limitePorUsuario(), request.canchas());
        return Response.created(UriBuilder.fromPath("/api/v1/admin/promociones/{id}")
                        .build(promocion.id()))
                .entity(AdminPromocionResponse.de(promocion))
                .build();
    }

    @POST
    @Path("/{idPromocion}/estado")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Activa o desactiva una promocion (409 si ya esta en ese estado)")
    public AdminPromocionResponse cambiarEstado(@PathParam("idPromocion") Long idPromocion,
                                                @Valid CambiarEstadoSimpleRequest request) {
        return AdminPromocionResponse.de(admin.cambiarEstadoPromocion(idPromocion,
                idAdministrador(), request.estado()));
    }

    private Long idAdministrador() {
        return Long.valueOf(jwt.getSubject());
    }
}
