package pe.com.canchadirecta.platform.adapter.in.rest;

import jakarta.annotation.security.RolesAllowed;
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
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import pe.com.canchadirecta.platform.adapter.in.rest.dto.AdminUsuarioResponse;
import pe.com.canchadirecta.platform.adapter.in.rest.dto.CambiarEstadoUsuarioRequest;
import pe.com.canchadirecta.platform.adapter.in.rest.dto.CambiarRolesRequest;
import pe.com.canchadirecta.platform.application.port.in.AdminPlataformaUseCase;

import java.util.List;

/**
 * Gestion de usuarios, roles y estado de cuenta por la administracion (CU06.1).
 */
@Path("/api/v1/admin/usuarios")
@RolesAllowed("PLATFORM_ADMIN")
@Tag(name = "admin", description = "Administracion de plataforma (PLATFORM_ADMIN)")
public class AdminUsuarioResource {

    private final AdminPlataformaUseCase admin;
    private final JsonWebToken jwt;

    @Inject
    public AdminUsuarioResource(AdminPlataformaUseCase admin, JsonWebToken jwt) {
        this.admin = admin;
        this.jwt = jwt;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Usuarios con sus roles; filtros opcionales por correo, estado y rol")
    public List<AdminUsuarioResponse> listar(@QueryParam("correo") String correo,
                                             @QueryParam("estado") String estado,
                                             @QueryParam("rol") String rol,
                                             @QueryParam("limite") @DefaultValue("50") int limite) {
        return admin.usuarios(correo, estado, rol, limite).stream()
                .map(AdminUsuarioResponse::de).toList();
    }

    @GET
    @Path("/{idUsuario}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Detalle de un usuario con sus roles")
    public AdminUsuarioResponse detalle(@PathParam("idUsuario") Long idUsuario) {
        return AdminUsuarioResponse.de(admin.usuario(idUsuario));
    }

    @POST
    @Path("/{idUsuario}/roles")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Reemplaza los roles del usuario y avisa (409 si dejaria la plataforma sin admin)")
    public AdminUsuarioResponse cambiarRoles(@PathParam("idUsuario") Long idUsuario,
                                             @Valid CambiarRolesRequest request) {
        return AdminUsuarioResponse.de(admin.cambiarRoles(idUsuario, idAdministrador(), request.roles()));
    }

    @POST
    @Path("/{idUsuario}/estado")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Activa, desactiva o bloquea la cuenta (409 si es el ultimo admin activo)")
    public AdminUsuarioResponse cambiarEstado(@PathParam("idUsuario") Long idUsuario,
                                              @Valid CambiarEstadoUsuarioRequest request) {
        return AdminUsuarioResponse.de(admin.cambiarEstado(idUsuario, idAdministrador(), request.estado()));
    }

    private Long idAdministrador() {
        return Long.valueOf(jwt.getSubject());
    }
}
