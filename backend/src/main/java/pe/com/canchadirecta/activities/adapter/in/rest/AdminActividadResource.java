package pe.com.canchadirecta.activities.adapter.in.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import pe.com.canchadirecta.activities.adapter.in.rest.dto.ActividadResponse;
import pe.com.canchadirecta.activities.application.port.in.ActividadesUseCase;

import java.util.List;

/** Supervision y cancelacion de actividades desde administracion. */
@Path("/api/v1/admin/actividades")
@RolesAllowed("PLATFORM_ADMIN")
@Tag(name = "admin", description = "Administracion de plataforma (PLATFORM_ADMIN)")
public class AdminActividadResource {

    private final ActividadesUseCase actividades;

    @Inject
    public AdminActividadResource(ActividadesUseCase actividades) {
        this.actividades = actividades;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Todas las actividades, incluidas las canceladas")
    public List<ActividadResponse> listar(@QueryParam("limite") @DefaultValue("100") int limite) {
        return actividades.todas(limite).stream().map(ActividadResponse::de).toList();
    }

    @DELETE
    @Path("/{idActividad}")
    @Operation(summary = "Cancela una actividad y sus inscripciones")
    public Response cancelar(@PathParam("idActividad") Long idActividad) {
        actividades.cancelarComoAdmin(idActividad);
        return Response.noContent().build();
    }
}
