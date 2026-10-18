package pe.com.canchadirecta.venues.adapter.in.rest;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import pe.com.canchadirecta.shared.domain.DomainException;
import pe.com.canchadirecta.shared.domain.ErrorCodes;
import pe.com.canchadirecta.venues.adapter.in.rest.dto.CanchaDetalleResponse;
import pe.com.canchadirecta.venues.application.port.in.CatalogoCanchasUseCase;

import java.util.ArrayList;
import java.util.List;

/**
 * Comparacion de canchas (CU02.2): devuelve la ficha de 2 a 4 canchas en el orden pedido para
 * que el cliente pueda enfrentar precio, capacidad, atributos, fotos y reputacion.
 *
 * <p>Se publica como recurso propio (`/api/v1/canchas/comparar`) porque la ruta literal gana a la
 * ficha `/api/v1/canchas/{idCancha}` en el matching de JAX-RS.</p>
 */
@Path("/api/v1/canchas/comparar")
@Tag(name = "catalogo", description = "Ubicaciones, deportes y ficha de cancha")
public class CompararCanchasResource {

    private final CatalogoCanchasUseCase catalogo;

    @Inject
    public CompararCanchasResource(CatalogoCanchasUseCase catalogo) {
        this.catalogo = catalogo;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Ficha de 2 a 4 canchas para compararlas (ids separados por coma)")
    public List<CanchaDetalleResponse> comparar(@QueryParam("ids") String ids) {
        return catalogo.comparar(ids(ids)).stream().map(CanchaDetalleResponse::de).toList();
    }

    private static List<Long> ids(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS,
                    "Indique las canchas a comparar en `ids` (separadas por coma).");
        }
        List<Long> valores = new ArrayList<>();
        for (String parte : texto.split(",")) {
            String limpio = parte.trim();
            try {
                valores.add(Long.valueOf(limpio));
            } catch (NumberFormatException ex) {
                throw new DomainException(ErrorCodes.DATOS_INVALIDOS,
                        "Los ids de las canchas deben ser numericos.");
            }
        }
        return valores;
    }
}
