package pe.com.canchadirecta.venues.adapter.in.rest;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
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
import pe.com.canchadirecta.shared.adapter.in.rest.dto.RecursoCreadoResponse;
import pe.com.canchadirecta.shared.domain.DomainException;
import pe.com.canchadirecta.shared.domain.ErrorCodes;
import pe.com.canchadirecta.venues.adapter.in.rest.dto.CrearBloqueoRequest;
import pe.com.canchadirecta.venues.adapter.in.rest.dto.CrearHorarioRequest;
import pe.com.canchadirecta.venues.application.dto.RegistrarBloqueoCommand;
import pe.com.canchadirecta.venues.application.dto.RegistrarHorarioCommand;
import pe.com.canchadirecta.venues.application.port.in.GestionAgendaUseCase;
import pe.com.canchadirecta.venues.domain.model.BloqueoCancha;
import pe.com.canchadirecta.venues.domain.model.HorarioCancha;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Adaptador de entrada REST de la agenda de una cancha: horarios de atencion (HU-005) y
 * bloqueos operativos (RN-03). Consultar horarios es publico; el resto exige ser el
 * propietario del establecimiento.
 */
@Path("/api/v1/canchas/{idCancha}")
@Tag(name = "venues", description = "Establecimientos y canchas")
public class AgendaResource {

    private final GestionAgendaUseCase agenda;
    private final JsonWebToken jwt;

    @Inject
    public AgendaResource(GestionAgendaUseCase agenda, JsonWebToken jwt) {
        this.agenda = agenda;
        this.jwt = jwt;
    }

    @GET
    @Path("/horarios")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Horarios de atencion activos de la cancha (publico)")
    public List<HorarioCancha> listarHorarios(@PathParam("idCancha") Long idCancha) {
        return agenda.listarHorarios(idCancha);
    }

    @POST
    @Path("/horarios")
    @Authenticated
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Registra un horario semanal en una cancha propia")
    public Response crearHorario(@PathParam("idCancha") Long idCancha,
                                 @Valid CrearHorarioRequest request) {
        Long id = agenda.registrarHorario(idUsuarioAutenticado(),
                new RegistrarHorarioCommand(idCancha, request.diaSemana(), request.horaInicio(),
                        request.horaFin(), request.precio()));
        return Response.created(UriBuilder.fromPath("/api/v1/canchas/{id}/horarios").build(id))
                .entity(new RecursoCreadoResponse(id))
                .build();
    }

    @DELETE
    @Path("/horarios/{idHorario}")
    @Authenticated
    @Operation(summary = "Desactiva un horario de una cancha propia")
    public Response eliminarHorario(@PathParam("idCancha") Long idCancha,
                                    @PathParam("idHorario") Long idHorario) {
        agenda.eliminarHorario(idUsuarioAutenticado(), idCancha, idHorario);
        return Response.noContent().build();
    }

    @GET
    @Path("/bloqueos")
    @Authenticated
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Bloqueos de la cancha en un rango (solo el propietario)")
    public List<BloqueoCancha> listarBloqueos(@PathParam("idCancha") Long idCancha,
                                              @QueryParam("desde") String desde,
                                              @QueryParam("hasta") String hasta) {
        return agenda.listarBloqueos(idUsuarioAutenticado(), idCancha,
                aInstante(desde, "desde"), aInstante(hasta, "hasta"));
    }

    @POST
    @Path("/bloqueos")
    @Authenticated
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Bloquea una franja de una cancha propia (409 si choca con una reserva)")
    public Response crearBloqueo(@PathParam("idCancha") Long idCancha,
                                 @Valid CrearBloqueoRequest request) {
        Long id = agenda.registrarBloqueo(idUsuarioAutenticado(),
                new RegistrarBloqueoCommand(idCancha, request.inicio(), request.fin(), request.motivo()));
        return Response.created(UriBuilder.fromPath("/api/v1/canchas/{id}/bloqueos").build(id))
                .entity(new RecursoCreadoResponse(id))
                .build();
    }

    @DELETE
    @Path("/bloqueos/{idBloqueo}")
    @Authenticated
    @Operation(summary = "Levanta un bloqueo de una cancha propia")
    public Response eliminarBloqueo(@PathParam("idCancha") Long idCancha,
                                    @PathParam("idBloqueo") Long idBloqueo) {
        agenda.eliminarBloqueo(idUsuarioAutenticado(), idCancha, idBloqueo);
        return Response.noContent().build();
    }

    private Long idUsuarioAutenticado() {
        return Long.valueOf(jwt.getSubject());
    }

    private static Instant aInstante(String valor, String nombre) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(valor.trim());
        } catch (DateTimeParseException ex) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS,
                    "El parametro " + nombre + " debe ser un instante ISO-8601 (por ejemplo 2026-11-02T18:00:00Z).");
        }
    }
}
