package pe.com.canchadirecta.reception.adapter.in.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
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
import pe.com.canchadirecta.reception.adapter.in.rest.dto.CobroRequest;
import pe.com.canchadirecta.reception.adapter.in.rest.dto.IncidenciaRequest;
import pe.com.canchadirecta.reception.adapter.in.rest.dto.NuevaReservaPresencialRequest;
import pe.com.canchadirecta.reception.application.dto.ClienteRecepcion;
import pe.com.canchadirecta.reception.application.dto.CanchaRecepcion;
import pe.com.canchadirecta.reception.application.dto.EstablecimientoRecepcion;
import pe.com.canchadirecta.reception.application.dto.IncidenciaRecepcion;
import pe.com.canchadirecta.reception.application.dto.ReservaPresencialCommand;
import pe.com.canchadirecta.reception.application.dto.ReservaRecepcion;
import pe.com.canchadirecta.reception.application.dto.ResumenTurno;
import pe.com.canchadirecta.reception.application.port.in.RecepcionUseCase;
import pe.com.canchadirecta.shared.domain.DomainException;
import pe.com.canchadirecta.shared.domain.ErrorCodes;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Portal de recepcion (Bloque D, ADR-0038).
 *
 * <p>Disponible para `OPERADOR`, `OWNER` y `PLATFORM_ADMIN`; el alcance por sede se resuelve
 * en el repositorio segun rol y asignaciones.</p>
 */
@Path("/api/v1/recepcion")
@RolesAllowed({"OPERADOR", "OWNER", "PLATFORM_ADMIN"})
@Tag(name = "recepcion", description = "Operacion diaria de recepcion (turno, agenda, reservas y cobros)")
public class RecepcionResource {

    private final RecepcionUseCase recepcion;
    private final JsonWebToken jwt;

    @Inject
    public RecepcionResource(RecepcionUseCase recepcion, JsonWebToken jwt) {
        this.recepcion = recepcion;
        this.jwt = jwt;
    }

    @GET
    @Path("/establecimientos")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Sedes a las que el usuario tiene acceso")
    public List<EstablecimientoRecepcion> establecimientos() {
        return recepcion.establecimientos(idUsuario());
    }

    @GET
    @Path("/canchas")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Canchas activas de las sedes accesibles")
    public List<CanchaRecepcion> canchas(@QueryParam("idEstablecimiento") Long idEstablecimiento) {
        return recepcion.canchas(idUsuario(), idEstablecimiento);
    }

    @GET
    @Path("/resumen")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "KPIs del turno para una fecha")
    public ResumenTurno resumen(@QueryParam("idEstablecimiento") Long idEstablecimiento,
                                @QueryParam("fecha") String fecha) {
        return recepcion.resumen(idUsuario(), idEstablecimiento, fecha(fecha));
    }

    @GET
    @Path("/agenda")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Agenda de reservas de una fecha")
    public List<ReservaRecepcion> agenda(@QueryParam("idEstablecimiento") Long idEstablecimiento,
                                         @QueryParam("fecha") String fecha) {
        return recepcion.agenda(idUsuario(), idEstablecimiento, fecha(fecha));
    }

    @GET
    @Path("/reservas")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Reservas de las sedes accesibles, opcionalmente por estado")
    public List<ReservaRecepcion> reservas(@QueryParam("idEstablecimiento") Long idEstablecimiento,
                                           @QueryParam("estado") String estado,
                                           @QueryParam("limite") Integer limite) {
        return recepcion.reservas(idUsuario(), idEstablecimiento, estado, limite == null ? 50 : limite);
    }

    @GET
    @Path("/clientes")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Clientes registrados para la reserva presencial")
    public List<ClienteRecepcion> clientes(@QueryParam("q") String texto) {
        return recepcion.clientes(texto);
    }

    @POST
    @Path("/reservas")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Registra una reserva presencial")
    public Response reservaPresencial(NuevaReservaPresencialRequest request) {
        ReservaRecepcion creada = recepcion.reservaPresencial(idUsuario(), new ReservaPresencialCommand(
                request.idEstablecimiento(), request.idCancha(), request.idCliente(), request.fecha(),
                request.horaInicio(), request.duracionMinutos() == null ? 60 : request.duracionMinutos(),
                request.pagoRecibido(), request.codigoOperacion()));
        return Response.status(Response.Status.CREATED).entity(creada).build();
    }

    @POST
    @Path("/reservas/{idReserva}/checkin")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Marca el ingreso del cliente (CONFIRMADA -> FINALIZADA)")
    public ReservaRecepcion checkIn(@PathParam("idReserva") Long idReserva) {
        return recepcion.checkIn(idUsuario(), idReserva);
    }

    @POST
    @Path("/reservas/{idReserva}/cobro")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Registra el pago de una reserva vigente")
    public ReservaRecepcion cobrar(@PathParam("idReserva") Long idReserva, CobroRequest request) {
        return recepcion.cobrar(idUsuario(), idReserva, request.medioPago(), request.codigoOperacion());
    }

    @GET
    @Path("/incidencias")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Incidencias registradas por el usuario")
    public List<IncidenciaRecepcion> incidencias(@QueryParam("limite") Integer limite) {
        return recepcion.incidencias(idUsuario(), limite == null ? 50 : limite);
    }

    @POST
    @Path("/incidencias")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Registra una incidencia operativa")
    public Response registrarIncidencia(IncidenciaRequest request) {
        IncidenciaRecepcion creada = recepcion.registrarIncidencia(idUsuario(), request.idReserva(),
                request.asunto(), request.descripcion());
        return Response.status(Response.Status.CREATED).entity(creada).build();
    }

    private Long idUsuario() {
        return Long.valueOf(jwt.getSubject());
    }

    private static LocalDate fecha(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(valor.trim());
        } catch (DateTimeParseException error) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "La fecha no tiene el formato yyyy-MM-dd.");
        }
    }
}
