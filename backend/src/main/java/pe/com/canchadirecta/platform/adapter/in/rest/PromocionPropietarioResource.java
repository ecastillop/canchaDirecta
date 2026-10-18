package pe.com.canchadirecta.platform.adapter.in.rest;

import io.quarkus.security.Authenticated;
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
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import pe.com.canchadirecta.platform.adapter.in.rest.dto.AdminPromocionResponse;
import pe.com.canchadirecta.platform.adapter.in.rest.dto.CambiarEstadoSimpleRequest;
import pe.com.canchadirecta.platform.adapter.in.rest.dto.CrearPromocionPropietarioRequest;
import pe.com.canchadirecta.platform.application.port.in.PromocionesPropietarioUseCase;

import java.util.List;

/**
 * Promociones del establecimiento gestionadas por su propietario (CU04.6). El rol `OWNER` se
 * resuelve por propiedad del establecimiento, igual que el resto de endpoints de venues.
 */
@Path("/api/v1/establecimientos/{idEstablecimiento}/promociones")
@Authenticated
@Tag(name = "venues", description = "Establecimientos, canchas, agenda y promociones del propietario")
public class PromocionPropietarioResource {

    private final PromocionesPropietarioUseCase promociones;
    private final JsonWebToken jwt;

    @Inject
    public PromocionPropietarioResource(PromocionesPropietarioUseCase promociones, JsonWebToken jwt) {
        this.promociones = promociones;
        this.jwt = jwt;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Promociones que tocan las canchas del establecimiento")
    public List<AdminPromocionResponse> listar(@PathParam("idEstablecimiento") Long idEstablecimiento,
                                               @QueryParam("limite") @DefaultValue("50") int limite) {
        return promociones.listar(idEstablecimiento, idUsuario(), limite).stream()
                .map(AdminPromocionResponse::de).toList();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Crea una promocion para todas las canchas del establecimiento (409 si el codigo existe)")
    public Response crear(@PathParam("idEstablecimiento") Long idEstablecimiento,
                          @Valid CrearPromocionPropietarioRequest request) {
        var promocion = promociones.crear(idEstablecimiento, idUsuario(), request.codigo(),
                request.descripcion(), request.tipoDescuento(), request.valor(),
                request.fechaInicio(), request.fechaFin(), request.limiteUsos(),
                request.limitePorUsuario());
        return Response.created(UriBuilder.fromPath("/api/v1/establecimientos/{id}/promociones/{idPromocion}")
                        .build(idEstablecimiento, promocion.id()))
                .entity(AdminPromocionResponse.de(promocion))
                .build();
    }

    @POST
    @Path("/{idPromocion}/estado")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Activa o desactiva una promocion del establecimiento")
    public AdminPromocionResponse cambiarEstado(@PathParam("idEstablecimiento") Long idEstablecimiento,
                                                @PathParam("idPromocion") Long idPromocion,
                                                @Valid CambiarEstadoSimpleRequest request) {
        return AdminPromocionResponse.de(promociones.cambiarEstado(idEstablecimiento, idPromocion,
                idUsuario(), request.estado()));
    }

    private Long idUsuario() {
        return Long.valueOf(jwt.getSubject());
    }
}
