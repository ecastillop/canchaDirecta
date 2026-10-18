package pe.com.canchadirecta.engagement.adapter.in.rest;

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
import pe.com.canchadirecta.engagement.adapter.in.rest.dto.CalificacionResponse;
import pe.com.canchadirecta.engagement.adapter.in.rest.dto.CalificacionesCanchaResponse;
import pe.com.canchadirecta.engagement.application.dto.ResumenCalificaciones;
import pe.com.canchadirecta.engagement.application.port.in.ConsultarCalificacionesUseCase;
import pe.com.canchadirecta.engagement.domain.model.Calificacion;

/**
 * Adaptador de entrada REST publico de las calificaciones de una cancha (CU02.5). Forma
 * parte del detalle de cancha del catalogo, por eso no exige token.
 */
@Path("/api/v1/canchas/{idCancha}/calificaciones")
@Tag(name = "engagement", description = "Calificaciones de reservas finalizadas")
public class CalificacionesCanchaResource {

    private final ConsultarCalificacionesUseCase consultar;

    @Inject
    public CalificacionesCanchaResource(ConsultarCalificacionesUseCase consultar) {
        this.consultar = consultar;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Calificaciones publicas de una cancha (total, promedio y las mas recientes)")
    public CalificacionesCanchaResponse deCancha(@PathParam("idCancha") Long idCancha,
                                                 @QueryParam("limite") @DefaultValue("20") int limite) {
        ResumenCalificaciones resumen = consultar.deCancha(idCancha, limite);
        return new CalificacionesCanchaResponse(
                resumen.idCancha(),
                resumen.total(),
                resumen.promedio(),
                resumen.calificaciones().stream().map(CalificacionesCanchaResource::aRespuesta).toList());
    }

    private static CalificacionResponse aRespuesta(Calificacion calificacion) {
        return new CalificacionResponse(
                calificacion.id(),
                calificacion.idReserva(),
                calificacion.puntaje(),
                calificacion.comentario(),
                calificacion.fechaRegistro());
    }
}
