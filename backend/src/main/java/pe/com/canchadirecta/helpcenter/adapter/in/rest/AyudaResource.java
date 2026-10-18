package pe.com.canchadirecta.helpcenter.adapter.in.rest;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import pe.com.canchadirecta.helpcenter.application.port.in.AyudaUseCase;
import pe.com.canchadirecta.helpcenter.domain.model.AyudaCategoria;

import java.util.List;

/** Centro de ayuda publico con preguntas frecuentes. */
@Path("/api/v1/ayuda")
@Tag(name = "help", description = "Centro de ayuda y preguntas frecuentes")
public class AyudaResource {

    private final AyudaUseCase ayuda;

    @Inject
    public AyudaResource(AyudaUseCase ayuda) {
        this.ayuda = ayuda;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Categorias con sus preguntas frecuentes; admite busqueda por texto")
    public List<AyudaCategoria> listar(@QueryParam("texto") String texto) {
        return ayuda.listar(texto);
    }
}
