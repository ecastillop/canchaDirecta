package pe.com.canchadirecta.bookings.adapter.in.rest;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import pe.com.canchadirecta.bookings.adapter.in.rest.dto.DisponibilidadResponse;
import pe.com.canchadirecta.bookings.application.dto.DisponibilidadDia;
import pe.com.canchadirecta.bookings.application.port.in.ConsultarDisponibilidadUseCase;
import pe.com.canchadirecta.shared.domain.DomainException;
import pe.com.canchadirecta.shared.domain.ErrorCodes;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Disponibilidad publica de una cancha (no requiere token: la busqueda de canchas
 * tambien es publica). La fecha se interpreta en la zona de operacion del negocio.
 */
@Path("/api/v1/canchas/{idCancha}/disponibilidad")
@Tag(name = "bookings", description = "Reservas de canchas")
public class DisponibilidadResource {

    private final ConsultarDisponibilidadUseCase disponibilidad;

    @Inject
    public DisponibilidadResource(ConsultarDisponibilidadUseCase disponibilidad) {
        this.disponibilidad = disponibilidad;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Lista las franjas libres de una cancha en una fecha (AAAA-MM-DD)")
    public DisponibilidadResponse consultar(
            @PathParam("idCancha") Long idCancha,
            @QueryParam("fecha") String fecha,
            @QueryParam("duracionMinutos") @DefaultValue("60") long duracionMinutos) {

        if (fecha == null || fecha.isBlank()) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "La fecha es obligatoria (formato AAAA-MM-DD).");
        }
        LocalDate dia;
        try {
            dia = LocalDate.parse(fecha.trim());
        } catch (DateTimeParseException ex) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "La fecha debe tener el formato AAAA-MM-DD.");
        }

        DisponibilidadDia resultado = disponibilidad.consultar(idCancha, dia, duracionMinutos);
        return new DisponibilidadResponse(
                resultado.canchaId(),
                resultado.fecha().toString(),
                resultado.zonaHoraria(),
                resultado.duracionMinutos(),
                resultado.franjas().stream()
                        .map(franja -> new DisponibilidadResponse.Franja(
                                franja.franja().inicio(), franja.franja().fin(), franja.precio()))
                        .toList());
    }
}
