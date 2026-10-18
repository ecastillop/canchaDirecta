package pe.com.canchadirecta.engagement.adapter.in.rest;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import pe.com.canchadirecta.engagement.adapter.in.rest.dto.CalificacionResponse;
import pe.com.canchadirecta.engagement.adapter.in.rest.dto.CalificarReservaRequest;
import pe.com.canchadirecta.engagement.application.port.in.CalificarReservaUseCase;
import pe.com.canchadirecta.engagement.domain.model.Calificacion;

/**
 * Adaptador de entrada REST para calificar una reserva (CU03.8). El titular se toma del
 * `sub` del JWT, nunca del cuerpo de la peticion.
 */
@Path("/api/v1/reservas")
@Authenticated
@Tag(name = "engagement", description = "Calificaciones de reservas finalizadas")
public class CalificacionReservaResource {

    private final CalificarReservaUseCase calificar;
    private final JsonWebToken jwt;

    @Inject
    public CalificacionReservaResource(CalificarReservaUseCase calificar, JsonWebToken jwt) {
        this.calificar = calificar;
        this.jwt = jwt;
    }

    @POST
    @Path("/{idReserva}/calificacion")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Califica una reserva propia ya finalizada (409 si aun no finaliza o ya tiene calificacion)")
    public Response calificar(@PathParam("idReserva") Long idReserva,
                              @Valid CalificarReservaRequest request) {
        Calificacion creada = calificar.calificar(idReserva, idUsuarioAutenticado(),
                request.puntaje(), request.comentario());
        return Response.created(UriBuilder.fromPath("/api/v1/reservas/{idReserva}/calificacion")
                        .build(idReserva))
                .entity(new CalificacionResponse(creada.id(), creada.idReserva(), creada.puntaje(),
                        creada.comentario(), creada.fechaRegistro()))
                .build();
    }

    private Long idUsuarioAutenticado() {
        return Long.valueOf(jwt.getSubject());
    }
}
