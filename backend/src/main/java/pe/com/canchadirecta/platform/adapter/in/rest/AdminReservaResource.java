package pe.com.canchadirecta.platform.adapter.in.rest;

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
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import pe.com.canchadirecta.bookings.adapter.in.rest.dto.RechazarReservaRequest;
import pe.com.canchadirecta.bookings.adapter.in.rest.dto.ReservaResponse;
import pe.com.canchadirecta.bookings.application.port.in.AtenderReservaUseCase;
import pe.com.canchadirecta.platform.adapter.in.rest.dto.AdminSupervisionResponse;
import pe.com.canchadirecta.platform.application.port.in.AdminPlataformaUseCase;

import java.util.List;

/**
 * Supervision de reservas (CU06.4).
 */
@Path("/api/v1/admin/reservas")
@RolesAllowed("PLATFORM_ADMIN")
@Tag(name = "admin", description = "Administracion de plataforma (PLATFORM_ADMIN)")
public class AdminReservaResource {

    private final AdminPlataformaUseCase admin;
    private final AtenderReservaUseCase reservas;

    @Inject
    public AdminReservaResource(AdminPlataformaUseCase admin, AtenderReservaUseCase reservas) {
        this.admin = admin;
        this.reservas = reservas;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Reservas de todos los usuarios con cancha, establecimiento y titular")
    public List<AdminSupervisionResponse.Reserva> listar(@QueryParam("estado") String estado,
                                                         @QueryParam("limite") @DefaultValue("50") int limite) {
        return admin.reservas(estado, limite).stream().map(AdminSupervisionResponse.Reserva::de).toList();
    }

    @POST
    @Path("/{idReserva}/confirmar")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Confirma una reserva pendiente desde administracion")
    public ReservaResponse confirmar(@PathParam("idReserva") Long idReserva) {
        return ReservaResponse.de(reservas.confirmarComoAdmin(idReserva));
    }

    @POST
    @Path("/{idReserva}/rechazar")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Rechaza una reserva pendiente desde administracion y libera la franja")
    public ReservaResponse rechazar(@PathParam("idReserva") Long idReserva,
                                    @Valid RechazarReservaRequest request) {
        return ReservaResponse.de(reservas.rechazarComoAdmin(idReserva,
                request == null ? null : request.motivo()));
    }
}
