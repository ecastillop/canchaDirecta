package pe.com.canchadirecta.platform.adapter.in.rest;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import pe.com.canchadirecta.platform.adapter.in.rest.dto.SolicitarPropietarioRequest;
import pe.com.canchadirecta.platform.application.port.in.SolicitudPropietarioUseCase;
import pe.com.canchadirecta.platform.domain.model.PropietarioAdmin;
import pe.com.canchadirecta.shared.domain.DomainException;
import pe.com.canchadirecta.shared.domain.ErrorCodes;

/** Solicitud de alta como propietario y consulta de su estado (CU01.6). */
@Path("/api/v1/propietarios")
@Authenticated
@Tag(name = "propietarios", description = "Solicitud y estado de la cuenta de propietario")
public class PropietarioResource {

    private final SolicitudPropietarioUseCase solicitudes;
    private final JsonWebToken jwt;

    @Inject
    public PropietarioResource(SolicitudPropietarioUseCase solicitudes, JsonWebToken jwt) {
        this.solicitudes = solicitudes;
        this.jwt = jwt;
    }

    @POST
    @Path("/solicitudes")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Registra la solicitud de propietario (queda PENDIENTE de validacion)")
    public Response solicitar(@Valid SolicitarPropietarioRequest request) {
        PropietarioAdmin propietario = solicitudes.solicitar(idUsuario(), request.documentoIdentidad());
        return Response.status(Response.Status.CREATED).entity(propietario).build();
    }

    @GET
    @Path("/me")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Consulta la solicitud de propietario de la cuenta autenticada")
    public PropietarioAdmin consultar() {
        return solicitudes.consultar(idUsuario())
                .orElseThrow(() -> new DomainException(ErrorCodes.NO_ENCONTRADO,
                        "La cuenta no tiene solicitud de propietario."));
    }

    private Long idUsuario() {
        return Long.valueOf(jwt.getSubject());
    }
}
