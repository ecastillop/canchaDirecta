package pe.com.canchadirecta.engagement.adapter.in.rest;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import pe.com.canchadirecta.engagement.adapter.in.rest.dto.NotificacionesResponse;
import pe.com.canchadirecta.engagement.application.port.in.NotificacionesUseCase;

/**
 * Bandeja de avisos in-app del usuario autenticado.
 */
@Path("/api/v1/notificaciones")
@Authenticated
@Tag(name = "engagement", description = "Reclamos de soporte y avisos del usuario")
public class NotificacionResource {

    private final NotificacionesUseCase notificaciones;
    private final JsonWebToken jwt;

    @Inject
    public NotificacionResource(NotificacionesUseCase notificaciones, JsonWebToken jwt) {
        this.notificaciones = notificaciones;
        this.jwt = jwt;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Avisos del usuario con el total sin leer (mas recientes primero)")
    public NotificacionesResponse bandeja(@QueryParam("limite") @DefaultValue("20") int limite) {
        return NotificacionesResponse.de(notificaciones.bandeja(idUsuarioAutenticado(), limite));
    }

    @POST
    @Path("/{idNotificacion}/leida")
    @Operation(summary = "Marca un aviso propio como leido (404 si no existe o es ajeno)")
    public Response marcarLeida(@PathParam("idNotificacion") Long idNotificacion) {
        notificaciones.marcarLeida(idNotificacion, idUsuarioAutenticado());
        return Response.noContent().build();
    }

    private Long idUsuarioAutenticado() {
        return Long.valueOf(jwt.getSubject());
    }
}
