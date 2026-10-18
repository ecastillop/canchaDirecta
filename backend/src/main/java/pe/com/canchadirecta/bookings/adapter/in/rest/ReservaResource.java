package pe.com.canchadirecta.bookings.adapter.in.rest;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
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
import pe.com.canchadirecta.bookings.adapter.in.rest.dto.CrearReservaRequest;
import pe.com.canchadirecta.bookings.adapter.in.rest.dto.ModificarReservaRequest;
import pe.com.canchadirecta.bookings.adapter.in.rest.dto.RechazarReservaRequest;
import pe.com.canchadirecta.bookings.adapter.in.rest.dto.ReservaResponse;
import pe.com.canchadirecta.bookings.application.dto.CrearReservaCommand;
import pe.com.canchadirecta.bookings.application.port.in.AtenderReservaUseCase;
import pe.com.canchadirecta.bookings.application.port.in.CancelarReservaUseCase;
import pe.com.canchadirecta.bookings.application.port.in.ReservasUseCase;
import pe.com.canchadirecta.bookings.domain.model.Reserva;
import pe.com.canchadirecta.shared.domain.DomainException;
import pe.com.canchadirecta.shared.domain.ErrorCodes;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

/**
 * Adaptador de entrada REST de reservas. Todas las rutas exigen token de acceso;
 * el titular se toma del `sub` del JWT, nunca del cuerpo de la peticion.
 */
@Path("/api/v1/reservas")
@Authenticated
@Tag(name = "bookings", description = "Reservas de canchas")
public class ReservaResource {

    private final ReservasUseCase reservas;
    private final CancelarReservaUseCase cancelaciones;
    private final AtenderReservaUseCase atencion;
    private final JsonWebToken jwt;

    @Inject
    public ReservaResource(ReservasUseCase reservas, CancelarReservaUseCase cancelaciones,
                           AtenderReservaUseCase atencion, JsonWebToken jwt) {
        this.reservas = reservas;
        this.cancelaciones = cancelaciones;
        this.atencion = atencion;
        this.jwt = jwt;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Crea una reserva (409 SLOT_UNAVAILABLE si la franja ya esta ocupada)")
    public Response crear(@Valid CrearReservaRequest request,
                          @HeaderParam("Idempotency-Key") String claveIdempotencia) {
        Reserva creada = reservas.crear(new CrearReservaCommand(
                idUsuarioAutenticado(), request.canchaId(), request.inicio(), request.fin(),
                request.cocheraId(), request.codigoPromocion(), claveIdempotencia));
        return Response.created(UriBuilder.fromPath("/api/v1/reservas/{id}").build(creada.id()))
                .entity(aRespuesta(creada))
                .build();
    }

    @POST
    @Path("/{idReserva}/modificar")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Reprograma una reserva propia vigente conservando duracion e importes (CU03.7)")
    public ReservaResponse modificar(@PathParam("idReserva") Long idReserva,
                                     @Valid ModificarReservaRequest request) {
        return aRespuesta(reservas.modificar(idUsuarioAutenticado(), idReserva,
                request.inicio(), request.fin()));
    }

    @GET
    @Path("/mias")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Lista las reservas del usuario autenticado")
    public List<ReservaResponse> mias(@QueryParam("limite") @DefaultValue("50") int limite) {
        return reservas.listarMias(idUsuarioAutenticado(), limite).stream()
                .map(ReservaResource::aRespuesta)
                .toList();
    }

    @POST
    @Path("/{idReserva}/cancelar")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Cancela una reserva propia (409 si ya empezo o falta menos de la ventana minima)")
    public ReservaResponse cancelar(@PathParam("idReserva") Long idReserva) {
        return aRespuesta(cancelaciones.cancelar(idReserva, idUsuarioAutenticado()));
    }

    @GET
    @Path("/recibidas")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Lista las reservas recibidas en las canchas del propietario (HU-008-2)")
    public List<ReservaResponse> recibidas(@QueryParam("estado") String estado,
                                           @QueryParam("idCancha") Long idCancha,
                                           @QueryParam("desde") String desde,
                                           @QueryParam("hasta") String hasta,
                                           @QueryParam("limite") @DefaultValue("50") int limite) {
        return atencion.recibidas(idUsuarioAutenticado(), estadoDe(estado), idCancha,
                        aInstante(desde, "desde"), aInstante(hasta, "hasta"), limite).stream()
                .map(ReservaResource::aRespuesta)
                .toList();
    }

    @POST
    @Path("/{idReserva}/confirmar")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Confirma una reserva pendiente de una cancha propia (409 si ya no esta pendiente)")
    public ReservaResponse confirmar(@PathParam("idReserva") Long idReserva) {
        return aRespuesta(atencion.confirmar(idReserva, idUsuarioAutenticado()));
    }

    @POST
    @Path("/{idReserva}/rechazar")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Rechaza una reserva pendiente de una cancha propia y libera la franja")
    public ReservaResponse rechazar(@PathParam("idReserva") Long idReserva,
                                    @Valid RechazarReservaRequest request) {
        return aRespuesta(atencion.rechazar(idReserva, idUsuarioAutenticado(),
                request == null ? null : request.motivo()));
    }

    @POST
    @Path("/{idReserva}/revocar")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Revoca una reserva ya confirmada de una cancha propia, con reembolso total (L-14)")
    public ReservaResponse revocar(@PathParam("idReserva") Long idReserva,
                                   @Valid RechazarReservaRequest request) {
        return aRespuesta(atencion.revocar(idReserva, idUsuarioAutenticado(),
                request == null ? null : request.motivo()));
    }

    /** Filtro opcional de estado para la bandeja del propietario; un valor raro es 400. */
    private static Reserva.Estado estadoDe(String estado) {
        if (estado == null || estado.isBlank()) {
            return null;
        }
        try {
            return Reserva.Estado.valueOf(estado.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS,
                    "Estado de reserva desconocido: " + estado + ".");
        }
    }

    /** Filtro opcional de instante ISO-8601 para el rango de la bandeja; en blanco no filtra. */
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

    private static ReservaResponse aRespuesta(Reserva reserva) {
        Reserva.CocheraReservada cochera = reserva.cochera();
        return new ReservaResponse(
                reserva.id(),
                reserva.idCancha(),
                reserva.franja().inicio(),
                reserva.franja().fin(),
                reserva.montoCancha(),
                reserva.montoDescuento(),
                reserva.estado().name(),
                reserva.fechaCreacion(),
                cochera == null ? null : cochera.idCochera(),
                cochera == null ? null : cochera.monto());
    }

    private Long idUsuarioAutenticado() {
        return Long.valueOf(jwt.getSubject());
    }
}
