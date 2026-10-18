package pe.com.canchadirecta.settlements.adapter.in.rest;

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
import pe.com.canchadirecta.settlements.adapter.in.rest.dto.GenerarLiquidacionRequest;
import pe.com.canchadirecta.settlements.adapter.in.rest.dto.LiquidacionResponse;
import pe.com.canchadirecta.settlements.application.dto.LiquidacionConDetalle;
import pe.com.canchadirecta.settlements.application.port.in.GestionarLiquidacionesUseCase;
import pe.com.canchadirecta.settlements.domain.model.DetalleLiquidacion;
import pe.com.canchadirecta.settlements.domain.model.Liquidacion;
import pe.com.canchadirecta.shared.domain.DomainException;
import pe.com.canchadirecta.shared.domain.ErrorCodes;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Adaptador de entrada REST de las liquidaciones del propietario (CU04.8 / CU05.4). El
 * propietario se toma del `sub` del JWT, nunca del cuerpo de la peticion.
 */
@Path("/api/v1/liquidaciones")
@Authenticated
@Tag(name = "settlements", description = "Comisiones, ingresos y liquidaciones")
public class LiquidacionResource {

    private final GestionarLiquidacionesUseCase liquidaciones;
    private final JsonWebToken jwt;

    @Inject
    public LiquidacionResource(GestionarLiquidacionesUseCase liquidaciones, JsonWebToken jwt) {
        this.liquidaciones = liquidaciones;
        this.jwt = jwt;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Genera la liquidacion de las reservas pagadas y confirmadas del periodo")
    public Response generar(@Valid GenerarLiquidacionRequest request) {
        LiquidacionConDetalle resultado = liquidaciones.generar(idUsuarioAutenticado(),
                fecha(request.periodoInicio(), "inicio"), fecha(request.periodoFin(), "fin"));
        return Response.created(UriBuilder.fromPath("/api/v1/liquidaciones/{id}")
                        .build(resultado.liquidacion().id()))
                .entity(aRespuesta(resultado))
                .build();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Liquidaciones del propietario autenticado, mas recientes primero")
    public List<LiquidacionResponse> listar() {
        return liquidaciones.listar(idUsuarioAutenticado()).stream()
                .map(cabecera -> aRespuesta(new LiquidacionConDetalle(cabecera, List.of())))
                .toList();
    }

    @GET
    @Path("/{idLiquidacion}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Detalle de una liquidacion del propietario autenticado")
    public LiquidacionResponse consultar(@PathParam("idLiquidacion") Long idLiquidacion) {
        return aRespuesta(liquidaciones.consultar(idUsuarioAutenticado(), idLiquidacion));
    }

    private static LiquidacionResponse aRespuesta(LiquidacionConDetalle resultado) {
        Liquidacion liquidacion = resultado.liquidacion();
        return new LiquidacionResponse(
                liquidacion.id(),
                liquidacion.periodoInicio().toString(),
                liquidacion.periodoFin().toString(),
                liquidacion.montoBruto(),
                liquidacion.montoComision(),
                liquidacion.montoNeto(),
                liquidacion.estado().name(),
                resultado.detalles().stream().map(LiquidacionResource::aRespuesta).toList());
    }

    private static LiquidacionResponse.Detalle aRespuesta(DetalleLiquidacion detalle) {
        return new LiquidacionResponse.Detalle(
                detalle.idReserva(),
                detalle.montoReserva(),
                detalle.porcentajeComision(),
                detalle.montoComision(),
                detalle.montoNeto());
    }

    private static LocalDate fecha(String valor, String campo) {
        try {
            return LocalDate.parse(valor.trim());
        } catch (DateTimeParseException | NullPointerException ex) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS,
                    "La fecha de " + campo + " debe tener el formato AAAA-MM-DD.");
        }
    }

    private Long idUsuarioAutenticado() {
        return Long.valueOf(jwt.getSubject());
    }
}
