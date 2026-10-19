package pe.com.canchadirecta.venues.adapter.in.rest;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
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
import pe.com.canchadirecta.shared.adapter.in.rest.dto.RecursoCreadoResponse;
import pe.com.canchadirecta.venues.adapter.in.rest.dto.ActualizarEstablecimientoRequest;
import pe.com.canchadirecta.venues.adapter.in.rest.dto.ActualizarIdsRequest;
import pe.com.canchadirecta.venues.adapter.in.rest.dto.CanchaDetalleResponse;
import pe.com.canchadirecta.venues.adapter.in.rest.dto.CrearCanchaRequest;
import pe.com.canchadirecta.venues.adapter.in.rest.dto.CrearCocheraRequest;
import pe.com.canchadirecta.venues.adapter.in.rest.dto.CrearEstablecimientoRequest;
import pe.com.canchadirecta.venues.adapter.in.rest.dto.CrearFotoRequest;
import pe.com.canchadirecta.venues.adapter.in.rest.dto.FichaEspacioResponse;
import pe.com.canchadirecta.venues.application.dto.ActualizarEstablecimientoCommand;
import pe.com.canchadirecta.venues.application.dto.RegistrarCanchaCommand;
import pe.com.canchadirecta.venues.application.dto.RegistrarCocheraCommand;
import pe.com.canchadirecta.venues.application.dto.RegistrarEstablecimientoCommand;
import pe.com.canchadirecta.venues.application.port.in.CatalogoCanchasUseCase;
import pe.com.canchadirecta.venues.application.port.in.GestionVenuesUseCase;
import pe.com.canchadirecta.venues.domain.model.Cancha;
import pe.com.canchadirecta.venues.domain.model.CatalogoCancha;
import pe.com.canchadirecta.venues.domain.model.Cochera;
import pe.com.canchadirecta.venues.domain.model.Establecimiento;

import java.math.BigDecimal;
import java.util.List;

/** Adaptador de entrada REST de establecimientos, canchas y cocheras. */
@Path("/api/v1")
@Tag(name = "venues", description = "Establecimientos y canchas")
public class VenuesResource {

    private final GestionVenuesUseCase gestion;
    private final CatalogoCanchasUseCase catalogo;
    private final JsonWebToken jwt;

    @Inject
    public VenuesResource(GestionVenuesUseCase gestion, CatalogoCanchasUseCase catalogo, JsonWebToken jwt) {
        this.gestion = gestion;
        this.catalogo = catalogo;
        this.jwt = jwt;
    }

    @POST
    @Path("/establecimientos")
    @Authenticated
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Registra un establecimiento; el usuario queda como propietario")
    public Response crearEstablecimiento(@Valid CrearEstablecimientoRequest request) {
        Long id = gestion.registrarEstablecimiento(idUsuarioAutenticado(),
                new RegistrarEstablecimientoCommand(request.documentoIdentidad(), request.idDistrito(),
                        request.nombre(), request.direccion(), request.referencia()));
        return Response.created(UriBuilder.fromPath("/api/v1/establecimientos/{id}").build(id))
                .entity(new RecursoCreadoResponse(id))
                .build();
    }

    @GET
    @Path("/favoritos")
    @Authenticated
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "IDs de canchas favoritas del usuario")
    public List<Long> listarFavoritos() {
        return gestion.listarFavoritos(idUsuarioAutenticado());
    }

    @POST
    @Path("/favoritos/{idCancha}")
    @Authenticated
    @Operation(summary = "Agrega una cancha a favoritos")
    public Response agregarFavorito(@PathParam("idCancha") Long idCancha) {
        gestion.agregarFavorito(idUsuarioAutenticado(), idCancha);
        return Response.noContent().build();
    }

    @DELETE
    @Path("/favoritos/{idCancha}")
    @Authenticated
    @Operation(summary = "Quita una cancha de favoritos")
    public Response quitarFavorito(@PathParam("idCancha") Long idCancha) {
        gestion.quitarFavorito(idUsuarioAutenticado(), idCancha);
        return Response.noContent().build();
    }

    @GET
    @Path("/establecimientos/mios")
    @Authenticated
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Establecimientos del usuario autenticado")
    public List<Establecimiento> listarEstablecimientosPropios() {
        return gestion.listarEstablecimientosPropios(idUsuarioAutenticado());
    }

    @GET
    @Path("/establecimientos/{id}")
    @Authenticated
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Datos de un establecimiento propio (vista de gestion)")
    public Establecimiento obtenerEstablecimiento(@PathParam("id") Long id) {
        return gestion.obtenerEstablecimiento(idUsuarioAutenticado(), id);
    }

    @GET
    @Path("/establecimientos/{id}/ficha")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Ficha publica del espacio con sus canchas, servicios, fotos y reputacion")
    public FichaEspacioResponse fichaEspacio(@PathParam("id") Long id) {
        return FichaEspacioResponse.de(catalogo.fichaEspacio(id));
    }

    @PUT
    @Path("/establecimientos/{id}")
    @Authenticated
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Edita los datos de un establecimiento propio (CU04.1)")
    public Establecimiento actualizarEstablecimiento(@PathParam("id") Long id,
                                                     @Valid ActualizarEstablecimientoRequest request) {
        return gestion.actualizarEstablecimiento(idUsuarioAutenticado(), id,
                new ActualizarEstablecimientoCommand(request.idDistrito(), request.nombre(),
                        request.direccion(), request.referencia()));
    }

    @DELETE
    @Path("/establecimientos/{id}")
    @Authenticated
    @Operation(summary = "Da de baja un establecimiento propio y sus canchas (CU04.2)")
    public Response darDeBajaEstablecimiento(@PathParam("id") Long id) {
        gestion.darDeBajaEstablecimiento(idUsuarioAutenticado(), id);
        return Response.noContent().build();
    }

    @POST
    @Path("/establecimientos/{id}/canchas")
    @Authenticated
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Registra una cancha en un establecimiento propio")
    public Response crearCancha(@PathParam("id") Long idEstablecimiento,
                                @Valid CrearCanchaRequest request) {
        Long id = gestion.registrarCancha(idUsuarioAutenticado(),
                new RegistrarCanchaCommand(idEstablecimiento, request.nombre(), request.descripcion(),
                        request.capacidad(), request.precioBase()));
        return Response.created(UriBuilder.fromPath("/api/v1/canchas/{id}").build(id))
                .entity(new RecursoCreadoResponse(id))
                .build();
    }

    @GET
    @Path("/canchas")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Lista canchas con filtros de distrito, deporte, precio maximo y texto")
    public Response listarCanchas(@QueryParam("idDistrito") Long idDistrito,
                                      @QueryParam("idEstablecimiento") Long idEstablecimiento,
                                      @QueryParam("idDeporte") Long idDeporte,
                                      @QueryParam("precioMax") BigDecimal precioMax,
                                      @QueryParam("q") String texto,
                                      @QueryParam("fecha") String fecha,
                                      @QueryParam("orden") @DefaultValue("destacados") String orden,
                                      @QueryParam("limite") @DefaultValue("50") int limite,
                                      @QueryParam("desplazamiento") @DefaultValue("0") int desplazamiento) {
        CatalogoCancha.Pagina pagina = catalogo.buscarCanchas(idDistrito, idEstablecimiento, idDeporte, precioMax,
                texto, fecha, orden, limite, desplazamiento);
        return Response.ok(pagina.contenido())
                .header("X-Total-Count", pagina.total())
                .header("Access-Control-Expose-Headers", "X-Total-Count")
                .build();
    }

    @POST
    @Path("/establecimientos/{id}/cocheras")
    @Authenticated
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Registra una cochera (RN-08) con capacidad y tarifa por hora")
    public Response crearCochera(@PathParam("id") Long idEstablecimiento,
                                 @Valid CrearCocheraRequest request) {
        Long id = gestion.registrarCochera(idUsuarioAutenticado(),
                new RegistrarCocheraCommand(idEstablecimiento, request.nombre(),
                        request.capacidad(), request.tarifaHora()));
        return Response.created(UriBuilder.fromPath("/api/v1/establecimientos/{id}/cocheras").build(id))
                .entity(new RecursoCreadoResponse(id))
                .build();
    }

    @GET
    @Path("/establecimientos/{id}/cocheras")
    @Authenticated
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Cocheras de un establecimiento propio")
    public List<Cochera> listarCocheras(@PathParam("id") Long idEstablecimiento) {
        return gestion.listarCocheras(idUsuarioAutenticado(), idEstablecimiento);
    }

    @DELETE
    @Path("/establecimientos/{id}/cocheras/{idCochera}")
    @Authenticated
    @Operation(summary = "Desactiva una cochera propia (baja logica)")
    public Response desactivarCochera(@PathParam("id") Long idEstablecimiento,
                                      @PathParam("idCochera") Long idCochera) {
        gestion.desactivarCochera(idUsuarioAutenticado(), idEstablecimiento, idCochera);
        return Response.noContent().build();
    }

    private Long idUsuarioAutenticado() {
        return Long.valueOf(jwt.getSubject());
    }
}
