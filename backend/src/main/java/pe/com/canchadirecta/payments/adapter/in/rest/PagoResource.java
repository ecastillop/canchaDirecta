package pe.com.canchadirecta.payments.adapter.in.rest;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
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
import pe.com.canchadirecta.payments.adapter.in.rest.dto.PagoRequest;
import pe.com.canchadirecta.payments.adapter.in.rest.dto.PagoResponse;
import pe.com.canchadirecta.payments.adapter.in.rest.dto.ReembolsoResponse;
import pe.com.canchadirecta.payments.application.dto.DetallePago;
import pe.com.canchadirecta.payments.application.port.in.PagosUseCase;
import pe.com.canchadirecta.payments.domain.model.Pago;
import pe.com.canchadirecta.payments.domain.model.Reembolso;

import java.util.List;

/**
 * Adaptador de entrada REST del pago simulado de una reserva (CU03.4 / CU05.1). El titular
 * se toma del `sub` del JWT, nunca del cuerpo de la peticion.
 */
@Path("/api/v1/reservas")
@Authenticated
@Tag(name = "payments", description = "Pago simulado y reembolsos")
public class PagoResource {

    private final PagosUseCase pagos;
    private final JsonWebToken jwt;

    @Inject
    public PagoResource(PagosUseCase pagos, JsonWebToken jwt) {
        this.pagos = pagos;
        this.jwt = jwt;
    }

    @POST
    @Path("/{idReserva}/pago")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Procesa el pago simulado de una reserva propia (409 si ya tiene un pago aprobado)")
    public Response pagar(@PathParam("idReserva") Long idReserva, @Valid PagoRequest request) {
        Pago pago = pagos.pagar(idUsuarioAutenticado(), idReserva,
                request.medioPago(), request.codigoOperacion());
        return Response.created(UriBuilder.fromPath("/api/v1/reservas/{idReserva}/pago").build(idReserva))
                .entity(aRespuesta(pago, List.of()))
                .build();
    }

    @GET
    @Path("/{idReserva}/pago")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Ultimo pago de una reserva propia con sus reembolsos")
    public PagoResponse consultar(@PathParam("idReserva") Long idReserva) {
        DetallePago detalle = pagos.consultar(idUsuarioAutenticado(), idReserva);
        return aRespuesta(detalle.pago(), detalle.reembolsos());
    }

    private static PagoResponse aRespuesta(Pago pago, List<Reembolso> reembolsos) {
        return new PagoResponse(
                pago.id(),
                pago.idReserva(),
                pago.monto(),
                pago.medioPago(),
                pago.codigoOperacion(),
                pago.estado().name(),
                pago.fechaPago(),
                reembolsos.stream().map(PagoResource::aRespuesta).toList());
    }

    private static ReembolsoResponse aRespuesta(Reembolso reembolso) {
        return new ReembolsoResponse(
                reembolso.id(),
                reembolso.monto(),
                reembolso.motivo(),
                reembolso.estado().name(),
                reembolso.fechaSolicitud(),
                reembolso.fechaResolucion());
    }

    private Long idUsuarioAutenticado() {
        return Long.valueOf(jwt.getSubject());
    }
}
