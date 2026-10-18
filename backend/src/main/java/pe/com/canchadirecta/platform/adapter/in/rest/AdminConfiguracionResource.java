package pe.com.canchadirecta.platform.adapter.in.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import pe.com.canchadirecta.platform.adapter.in.rest.dto.ActualizarConfiguracionRequest;
import pe.com.canchadirecta.platform.application.port.in.ConfiguracionPlataformaUseCase;
import pe.com.canchadirecta.platform.domain.model.ConfiguracionPlataformaAdmin;

/**
 * Configuracion persistente de la plataforma (ADR-0037).
 *
 * <p>GET devuelve comision, ventanas de cancelacion, anticipacion maxima y correo de soporte.
 * PUT reemplaza los cinco valores, valida rangos y deja auditoria.</p>
 */
@Path("/api/v1/admin/configuracion")
@RolesAllowed("PLATFORM_ADMIN")
@Tag(name = "admin", description = "Administracion de plataforma (PLATFORM_ADMIN)")
public class AdminConfiguracionResource {

    private final ConfiguracionPlataformaUseCase configuracion;
    private final JsonWebToken jwt;

    @Inject
    public AdminConfiguracionResource(ConfiguracionPlataformaUseCase configuracion, JsonWebToken jwt) {
        this.configuracion = configuracion;
        this.jwt = jwt;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Configuracion vigente de la plataforma")
    public ConfiguracionPlataformaAdmin consultar() {
        return configuracion.consultar();
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Actualiza comision, ventanas de cancelacion, anticipacion y correo de soporte")
    public ConfiguracionPlataformaAdmin actualizar(ActualizarConfiguracionRequest request) {
        return configuracion.actualizar(Long.valueOf(jwt.getSubject()),
                request.comisionPorcentaje(), request.horasCancelacionGratis(),
                request.horasMinimasCancelacion(), request.diasAnticipacionMax(),
                request.correoSoporte());
    }
}
