package pe.com.canchadirecta.shared.adapter.in.rest;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Endpoint de diagnostico del sistema. No contiene reglas de negocio.
 * Se usa para validar el arranque del backend y la conectividad basica.
 */
@Path("/api/v1/system")
@Tag(name = "system", description = "Diagnostico de la plataforma")
public class SystemResource {

    @GET
    @Path("/ping")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Verifica que el backend responde")
    public Map<String, Object> ping() {
        return Map.of(
                "application", "canchadirecta",
                "status", "ok",
                "timestamp", OffsetDateTime.now().toString()
        );
    }
}
