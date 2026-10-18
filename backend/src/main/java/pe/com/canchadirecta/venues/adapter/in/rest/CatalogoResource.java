package pe.com.canchadirecta.venues.adapter.in.rest;

import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import pe.com.canchadirecta.venues.domain.model.CatalogoCancha;
import pe.com.canchadirecta.venues.application.port.in.CatalogoCanchasUseCase;

import java.util.List;

/**
 * Catalogos publicos de ubicaciones, deportes y caracteristicas (F02).
 *
 * <p>La ficha de cancha y sus atributos viven en {@link VenuesResource} para compartir la ruta
 * literal `/canchas` (RESTEasy no resuelve bien dos recursos raiz con prefijos solapados).</p>
 */
@Path("/api/v1")
@Tag(name = "catalogo", description = "Ubicaciones, deportes y ficha de cancha")
public class CatalogoResource {

    private final CatalogoCanchasUseCase catalogo;

    @Inject
    public CatalogoResource(CatalogoCanchasUseCase catalogo) {
        this.catalogo = catalogo;
    }

    @GET
    @Path("/ubicaciones/distritos")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Distritos con provincia y departamento (filtro opcional por nombre)")
    public List<CatalogoCancha.Distrito> distritos(@QueryParam("q") String texto,
                                                   @QueryParam("limite") @DefaultValue("200") int limite) {
        return catalogo.distritos(texto, limite);
    }

    @GET
    @Path("/deportes")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Catalogo de deportes")
    public List<CatalogoCancha.Item> deportes() {
        return catalogo.deportes();
    }

    @GET
    @Path("/caracteristicas")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Catalogo de caracteristicas de cancha")
    public List<CatalogoCancha.Item> caracteristicas() {
        return catalogo.caracteristicas();
    }
}
