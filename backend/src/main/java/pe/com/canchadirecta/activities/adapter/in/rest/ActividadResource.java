package pe.com.canchadirecta.activities.adapter.in.rest;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
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
import pe.com.canchadirecta.activities.adapter.in.rest.dto.ActividadRequest;
import pe.com.canchadirecta.activities.adapter.in.rest.dto.ActividadResponse;
import pe.com.canchadirecta.activities.adapter.in.rest.dto.InscripcionResponse;
import pe.com.canchadirecta.activities.application.dto.InscripcionVista;
import pe.com.canchadirecta.activities.application.port.in.ActividadesUseCase;
import pe.com.canchadirecta.activities.domain.model.Actividad;
import pe.com.canchadirecta.shared.domain.DomainException;
import pe.com.canchadirecta.shared.domain.ErrorCodes;

import java.time.Instant;
import java.util.List;

/**
 * Actividades de los establecimientos: catalogo publico, gestion del propietario e
 * inscripciones de los clientes.
 */
@Path("/api/v1")
@Tag(name = "activities", description = "Actividades e inscripciones")
public class ActividadResource {

    private final ActividadesUseCase actividades;
    private final JsonWebToken jwt;

    @Inject
    public ActividadResource(ActividadesUseCase actividades, JsonWebToken jwt) {
        this.actividades = actividades;
        this.jwt = jwt;
    }

    @GET
    @Path("/actividades")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Actividades activas y vigentes, las mas proximas primero")
    public List<ActividadResponse> publicas(@QueryParam("desde") String desde,
                                            @QueryParam("limite") @DefaultValue("30") int limite) {
        return actividades.publicas(instante(desde), limite).stream()
                .map(ActividadResponse::de).toList();
    }

    @GET
    @Path("/actividades/{idActividad}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Detalle publico de una actividad")
    public ActividadResponse detalle(@PathParam("idActividad") Long idActividad) {
        return ActividadResponse.de(actividades.detalle(idActividad));
    }

    @POST
    @Path("/establecimientos/{idEstablecimiento}/actividades")
    @Authenticated
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Publica una actividad en un establecimiento propio")
    public Response crear(@PathParam("idEstablecimiento") Long idEstablecimiento,
                          @Valid ActividadRequest request) {
        Actividad creada = actividades.crear(idUsuarioAutenticado(), idEstablecimiento,
                request.aCommand());
        return Response.created(UriBuilder.fromPath("/api/v1/actividades/{id}")
                        .build(creada.id()))
                .entity(ActividadResponse.de(creada))
                .build();
    }

    @GET
    @Path("/establecimientos/{idEstablecimiento}/actividades")
    @Authenticated
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Actividades de un establecimiento propio, incluidas las canceladas")
    public List<ActividadResponse> delEstablecimiento(@PathParam("idEstablecimiento") Long idEstablecimiento) {
        return actividades.delEstablecimiento(idUsuarioAutenticado(), idEstablecimiento).stream()
                .map(ActividadResponse::de).toList();
    }

    @PUT
    @Path("/actividades/{idActividad}")
    @Authenticated
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Edita una actividad propia (409 si esta cancelada o hay cupos ocupados)")
    public ActividadResponse actualizar(@PathParam("idActividad") Long idActividad,
                                        @Valid ActividadRequest request) {
        return ActividadResponse.de(actividades.actualizar(idUsuarioAutenticado(), idActividad,
                request.aCommand()));
    }

    @DELETE
    @Path("/actividades/{idActividad}")
    @Authenticated
    @Operation(summary = "Cancela una actividad propia y sus inscripciones")
    public Response cancelar(@PathParam("idActividad") Long idActividad) {
        actividades.cancelar(idUsuarioAutenticado(), idActividad);
        return Response.noContent().build();
    }

    @POST
    @Path("/actividades/{idActividad}/inscripciones")
    @Authenticated
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Inscribe al usuario autenticado (409 sin cupos o ya inscrito)")
    public Response inscribir(@PathParam("idActividad") Long idActividad) {
        InscripcionVista inscripcion = actividades.inscribir(idUsuarioAutenticado(), idActividad);
        return Response.created(UriBuilder.fromPath("/api/v1/actividades/{id}/inscripciones")
                        .build(idActividad))
                .entity(InscripcionResponse.de(inscripcion))
                .build();
    }

    @DELETE
    @Path("/actividades/{idActividad}/inscripciones")
    @Authenticated
    @Operation(summary = "Cancela la inscripcion propia")
    public Response cancelarInscripcion(@PathParam("idActividad") Long idActividad) {
        actividades.cancelarInscripcion(idUsuarioAutenticado(), idActividad);
        return Response.noContent().build();
    }

    @GET
    @Path("/inscripciones/mias")
    @Authenticated
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Inscripciones del usuario, incluidas las canceladas")
    public List<InscripcionResponse> misInscripciones() {
        return actividades.misInscripciones(idUsuarioAutenticado()).stream()
                .map(InscripcionResponse::de).toList();
    }

    @GET
    @Path("/actividades/{idActividad}/inscripciones")
    @Authenticated
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Participantes confirmados; solo el propietario de la actividad")
    public List<InscripcionResponse> inscritos(@PathParam("idActividad") Long idActividad) {
        return actividades.inscritos(idUsuarioAutenticado(), idActividad).stream()
                .map(InscripcionResponse::de).toList();
    }

    private Long idUsuarioAutenticado() {
        return Long.valueOf(jwt.getSubject());
    }

    private static Instant instante(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(valor);
        } catch (RuntimeException ex) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS,
                    "La fecha 'desde' debe estar en formato ISO-8601.");
        }
    }
}
