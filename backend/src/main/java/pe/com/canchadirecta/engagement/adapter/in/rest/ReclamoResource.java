package pe.com.canchadirecta.engagement.adapter.in.rest;

import io.quarkus.security.Authenticated;
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
import pe.com.canchadirecta.engagement.adapter.in.rest.dto.ComentarReclamoRequest;
import pe.com.canchadirecta.engagement.adapter.in.rest.dto.CrearReclamoRequest;
import pe.com.canchadirecta.engagement.adapter.in.rest.dto.ReclamoDetalleResponse;
import pe.com.canchadirecta.engagement.adapter.in.rest.dto.ReclamoResponse;
import pe.com.canchadirecta.engagement.adapter.in.rest.dto.SeguimientoResponse;
import pe.com.canchadirecta.engagement.application.port.in.ReclamosUseCase;
import pe.com.canchadirecta.engagement.domain.model.Reclamo;
import pe.com.canchadirecta.engagement.domain.model.ReclamoSeguimiento;

import java.util.List;

/**
 * Soporte del usuario (CU06.6). El titular sale del `sub` del JWT: nadie ve ni comenta
 * reclamos ajenos.
 */
@Path("/api/v1/reclamos")
@Authenticated
@Tag(name = "engagement", description = "Reclamos de soporte y avisos del usuario")
public class ReclamoResource {

    private final ReclamosUseCase reclamos;
    private final JsonWebToken jwt;

    @Inject
    public ReclamoResource(ReclamosUseCase reclamos, JsonWebToken jwt) {
        this.reclamos = reclamos;
        this.jwt = jwt;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Registra un reclamo propio, opcionalmente sobre una reserva propia")
    public Response crear(@Valid CrearReclamoRequest request) {
        Reclamo creado = reclamos.crear(idUsuarioAutenticado(), request.idReserva(),
                request.asunto(), request.descripcion());
        return Response.created(UriBuilder.fromPath("/api/v1/reclamos/{id}").build(creado.id()))
                .entity(ReclamoResponse.de(creado))
                .build();
    }

    @GET
    @Path("/mios")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Reclamos del usuario, los mas recientes primero")
    public List<ReclamoResponse> mios(@QueryParam("limite") @DefaultValue("20") int limite) {
        return reclamos.mios(idUsuarioAutenticado(), limite).stream().map(ReclamoResponse::de).toList();
    }

    @GET
    @Path("/{idReclamo}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Detalle de un reclamo propio con su conversacion (403 si es ajeno)")
    public ReclamoDetalleResponse detalle(@PathParam("idReclamo") Long idReclamo) {
        return ReclamoDetalleResponse.de(reclamos.detalle(idReclamo, idUsuarioAutenticado()));
    }

    @POST
    @Path("/{idReclamo}/seguimientos")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Anade un comentario a un reclamo propio abierto (409 si esta cerrado)")
    public Response comentar(@PathParam("idReclamo") Long idReclamo,
                             @Valid ComentarReclamoRequest request) {
        ReclamoSeguimiento seguimiento = reclamos.comentar(idReclamo, idUsuarioAutenticado(),
                request.comentario());
        return Response.created(UriBuilder.fromPath("/api/v1/reclamos/{id}/seguimientos")
                        .build(idReclamo))
                .entity(SeguimientoResponse.de(seguimiento))
                .build();
    }

    private Long idUsuarioAutenticado() {
        return Long.valueOf(jwt.getSubject());
    }
}
