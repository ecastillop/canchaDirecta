package pe.com.canchadirecta.venues.adapter.in.rest;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import pe.com.canchadirecta.venues.adapter.in.rest.dto.ActualizarCanchaRequest;
import pe.com.canchadirecta.venues.adapter.in.rest.dto.ActualizarIdsRequest;
import pe.com.canchadirecta.venues.adapter.in.rest.dto.CanchaDetalleResponse;
import pe.com.canchadirecta.venues.adapter.in.rest.dto.CrearFotoRequest;
import pe.com.canchadirecta.venues.application.dto.ActualizarCanchaCommand;
import pe.com.canchadirecta.venues.application.port.in.CatalogoCanchasUseCase;
import pe.com.canchadirecta.venues.application.port.in.GestionVenuesUseCase;
import pe.com.canchadirecta.venues.domain.model.Cancha;
import pe.com.canchadirecta.venues.domain.model.CatalogoCancha;

/**
 * Ficha de cancha (CU02.1-CU02.3) y atributos administrables por el propietario (F03), mas la
 * edicion y baja logica de la propia cancha (CU04.1/CU04.2).
 *
 * <p>Comparte el prefijo literal `/api/v1/canchas/{idCancha}` con {@link AgendaResource}; ambos
 * declaran metodos distintos. La ficha no puede vivir en el recurso raiz `/api/v1` porque ese
 * prefijo queda sombreado por este recurso mas especifico (limitacion de RESTEasy ya documentada).</p>
 */
@Path("/api/v1/canchas/{idCancha}")
@Tag(name = "catalogo", description = "Ubicaciones, deportes y ficha de cancha")
public class CanchaDetalleResource {

    private final CatalogoCanchasUseCase catalogo;
    private final GestionVenuesUseCase gestion;
    private final JsonWebToken jwt;

    @Inject
    public CanchaDetalleResource(CatalogoCanchasUseCase catalogo, GestionVenuesUseCase gestion,
                                 JsonWebToken jwt) {
        this.catalogo = catalogo;
        this.gestion = gestion;
        this.jwt = jwt;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Ficha de la cancha con establecimiento, distrito, atributos, fotos y reputacion")
    public CanchaDetalleResponse detalle(@PathParam("idCancha") Long idCancha) {
        return CanchaDetalleResponse.de(catalogo.detalle(idCancha));
    }

    @PUT
    @Authenticated
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Edita los datos de una cancha propia (CU04.1)")
    public Cancha actualizar(@PathParam("idCancha") Long idCancha,
                             @Valid ActualizarCanchaRequest request) {
        return gestion.actualizarCancha(idUsuario(), idCancha,
                new ActualizarCanchaCommand(request.nombre(), request.descripcion(),
                        request.capacidad(), request.precioBase()));
    }

    @DELETE
    @Authenticated
    @Operation(summary = "Da de baja una cancha propia (CU04.2; baja logica)")
    public Response darDeBaja(@PathParam("idCancha") Long idCancha) {
        gestion.darDeBajaCancha(idUsuario(), idCancha);
        return Response.noContent().build();
    }

    @PUT
    @Path("/deportes")
    @Authenticated
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Reemplaza los deportes de la cancha (solo su propietario)")
    public CanchaDetalleResponse deportes(@PathParam("idCancha") Long idCancha,
                                          @Valid ActualizarIdsRequest request) {
        return CanchaDetalleResponse.de(catalogo.actualizarDeportes(idCancha, idUsuario(),
                request.ids()));
    }

    @PUT
    @Path("/caracteristicas")
    @Authenticated
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Reemplaza las caracteristicas de la cancha (solo su propietario)")
    public CanchaDetalleResponse caracteristicas(@PathParam("idCancha") Long idCancha,
                                                 @Valid ActualizarIdsRequest request) {
        return CanchaDetalleResponse.de(catalogo.actualizarCaracteristicas(idCancha, idUsuario(),
                request.ids()));
    }

    @POST
    @Path("/fotos")
    @Authenticated
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Anade una foto a la cancha (orden unico; 409 si ya existe)")
    public Response foto(@PathParam("idCancha") Long idCancha, @Valid CrearFotoRequest request) {
        CatalogoCancha.Foto foto = catalogo.agregarFoto(idCancha, idUsuario(), request.url(),
                request.orden(), request.principal());
        return Response.created(UriBuilder.fromPath("/api/v1/canchas/{id}/fotos/{idFoto}")
                        .build(idCancha, foto.id()))
                .entity(foto)
                .build();
    }

    @DELETE
    @Path("/fotos/{idFoto}")
    @Authenticated
    @Operation(summary = "Elimina una foto de la cancha (solo su propietario)")
    public Response eliminarFoto(@PathParam("idCancha") Long idCancha,
                                 @PathParam("idFoto") Long idFoto) {
        catalogo.eliminarFoto(idCancha, idUsuario(), idFoto);
        return Response.noContent().build();
    }

    private Long idUsuario() {
        return Long.valueOf(jwt.getSubject());
    }
}
