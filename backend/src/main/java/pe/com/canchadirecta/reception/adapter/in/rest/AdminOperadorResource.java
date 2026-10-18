package pe.com.canchadirecta.reception.adapter.in.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import pe.com.canchadirecta.reception.adapter.in.rest.dto.AsignarOperadorRequest;
import pe.com.canchadirecta.reception.application.dto.OperadorAsignado;
import pe.com.canchadirecta.reception.application.port.in.RecepcionAdminUseCase;

import java.util.List;

/** Personal y permisos de recepcion por sede (solo PLATFORM_ADMIN). */
@Path("/api/v1/admin/establecimientos/{idEstablecimiento}/operadores")
@RolesAllowed("PLATFORM_ADMIN")
@Tag(name = "admin", description = "Administracion de plataforma (PLATFORM_ADMIN)")
public class AdminOperadorResource {

    private final RecepcionAdminUseCase recepcion;
    private final JsonWebToken jwt;

    @Inject
    public AdminOperadorResource(RecepcionAdminUseCase recepcion, JsonWebToken jwt) {
        this.recepcion = recepcion;
        this.jwt = jwt;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Operadores vinculados a la sede")
    public List<OperadorAsignado> operadores(@PathParam("idEstablecimiento") Long idEstablecimiento) {
        return recepcion.operadores(idEstablecimiento);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Vincula un usuario como operador de la sede")
    public Response asignar(@PathParam("idEstablecimiento") Long idEstablecimiento,
                            AsignarOperadorRequest request) {
        recepcion.asignar(Long.valueOf(jwt.getSubject()), idEstablecimiento, request.idUsuario(),
                request.cargo());
        return Response.status(Response.Status.CREATED)
                .entity(recepcion.operadores(idEstablecimiento)).build();
    }

    @DELETE
    @Path("/{idUsuario}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Quita el acceso de un operador a la sede")
    public List<OperadorAsignado> remover(@PathParam("idEstablecimiento") Long idEstablecimiento,
                                          @PathParam("idUsuario") Long idUsuario) {
        recepcion.remover(Long.valueOf(jwt.getSubject()), idEstablecimiento, idUsuario);
        return recepcion.operadores(idEstablecimiento);
    }
}
