package pe.com.canchadirecta.engagement.adapter.in.rest;

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
import pe.com.canchadirecta.engagement.adapter.in.rest.dto.AdminReclamoDetalleResponse;
import pe.com.canchadirecta.engagement.adapter.in.rest.dto.AdminReclamoResponse;
import pe.com.canchadirecta.engagement.adapter.in.rest.dto.CambiarEstadoReclamoRequest;
import pe.com.canchadirecta.engagement.adapter.in.rest.dto.ComentarReclamoRequest;
import pe.com.canchadirecta.engagement.adapter.in.rest.dto.SeguimientoResponse;
import pe.com.canchadirecta.engagement.application.port.in.ModerarReclamosUseCase;
import pe.com.canchadirecta.engagement.domain.model.Reclamo;
import pe.com.canchadirecta.engagement.domain.model.ReclamoSeguimiento;

import java.util.List;

/**
 * Moderacion de reclamos para la administracion de plataforma (`PLATFORM_ADMIN`, F07).
 */
@Path("/api/v1/admin/reclamos")
@RolesAllowed("PLATFORM_ADMIN")
@Tag(name = "admin", description = "Moderacion de reclamos (PLATFORM_ADMIN)")
public class AdminReclamoResource {

    private final ModerarReclamosUseCase moderar;
    private final JsonWebToken jwt;

    @Inject
    public AdminReclamoResource(ModerarReclamosUseCase moderar, JsonWebToken jwt) {
        this.moderar = moderar;
        this.jwt = jwt;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Reclamos de todos los usuarios, con filtro opcional por estado")
    public List<AdminReclamoResponse> listar(@QueryParam("estado") String estado,
                                             @QueryParam("limite") @DefaultValue("50") int limite) {
        return moderar.listar(estado, limite).stream().map(AdminReclamoResponse::de).toList();
    }

    @GET
    @Path("/{idReclamo}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Detalle de un reclamo con su conversacion")
    public AdminReclamoDetalleResponse detalle(@PathParam("idReclamo") Long idReclamo) {
        return AdminReclamoDetalleResponse.de(moderar.detalle(idReclamo));
    }

    @POST
    @Path("/{idReclamo}/estado")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Cambia el estado con transiciones validas y avisa al titular (409 si no procede)")
    public AdminReclamoResponse cambiarEstado(@PathParam("idReclamo") Long idReclamo,
                                              @Valid CambiarEstadoReclamoRequest request) {
        Reclamo reclamo = moderar.cambiarEstado(idReclamo, idAdministrador(),
                request.estado(), request.comentario());
        return AdminReclamoResponse.de(reclamo);
    }

    @POST
    @Path("/{idReclamo}/seguimientos")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Respuesta oficial al titular (409 si el reclamo esta cerrado)")
    public Response responder(@PathParam("idReclamo") Long idReclamo,
                              @Valid ComentarReclamoRequest request) {
        ReclamoSeguimiento seguimiento = moderar.responder(idReclamo, idAdministrador(),
                request.comentario());
        return Response.created(UriBuilder.fromPath("/api/v1/admin/reclamos/{id}/seguimientos")
                        .build(idReclamo))
                .entity(SeguimientoResponse.de(seguimiento))
                .build();
    }

    private Long idAdministrador() {
        return Long.valueOf(jwt.getSubject());
    }
}
