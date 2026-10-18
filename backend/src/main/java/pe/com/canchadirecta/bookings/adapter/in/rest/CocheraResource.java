package pe.com.canchadirecta.bookings.adapter.in.rest;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import pe.com.canchadirecta.bookings.adapter.in.rest.dto.CocheraDisponibilidadResponse;
import pe.com.canchadirecta.bookings.application.port.in.ConsultarCocherasUseCase;
import pe.com.canchadirecta.shared.domain.DomainException;
import pe.com.canchadirecta.shared.domain.ErrorCodes;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Cupo publico de las cocheras del establecimiento de una cancha (RN-08). No requiere
 * token: el usuario consulta la cochera opcional antes de reservar, igual que la
 * disponibilidad de la cancha.
 */
@Path("/api/v1/canchas/{idCancha}/cocheras")
@Tag(name = "bookings", description = "Reservas de canchas")
public class CocheraResource {

    private final ConsultarCocherasUseCase cocheras;

    @Inject
    public CocheraResource(ConsultarCocherasUseCase cocheras) {
        this.cocheras = cocheras;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Cocheras del establecimiento con su cupo libre en un intervalo (ISO-8601 UTC)")
    public List<CocheraDisponibilidadResponse> consultar(
            @PathParam("idCancha") Long idCancha,
            @QueryParam("inicio") String inicio,
            @QueryParam("fin") String fin) {

        return cocheras.consultar(idCancha, aInstante(inicio, "inicio"), aInstante(fin, "fin")).stream()
                .map(cochera -> new CocheraDisponibilidadResponse(
                        cochera.id(), cochera.nombre(), cochera.capacidad(),
                        cochera.cupoDisponible(), cochera.tarifaHora()))
                .toList();
    }

    private static Instant aInstante(String valor, String nombre) {
        if (valor == null || valor.isBlank()) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS,
                    "El parametro " + nombre + " es obligatorio.");
        }
        try {
            return Instant.parse(valor.trim());
        } catch (DateTimeParseException ex) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS,
                    "El parametro " + nombre + " debe ser un instante ISO-8601 (por ejemplo 2026-11-02T18:00:00Z).");
        }
    }
}
