package pe.com.canchadirecta.identity.adapter.in.rest;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import pe.com.canchadirecta.shared.domain.DomainException;
import pe.com.canchadirecta.shared.domain.ErrorCodes;
import pe.com.canchadirecta.identity.adapter.in.rest.dto.ActualizarPerfilRequest;
import pe.com.canchadirecta.identity.application.dto.ActualizarPerfilCommand;
import pe.com.canchadirecta.identity.application.port.in.ActualizarPerfilUseCase;
import pe.com.canchadirecta.identity.application.port.out.UsuarioRepositorio;
import pe.com.canchadirecta.identity.domain.model.Usuario;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Adaptador de entrada REST para el perfil de la sesion autenticada (CU01.5).
 * El control de acceso se declara con @Authenticated; el recurso no valida el
 * token por si mismo (lo hace la extension de seguridad).
 */
@Path("/api/v1/users")
@Authenticated
@Tag(name = "auth", description = "Registro, sesion y cuenta")
public class MeResource {

    @Inject
    JsonWebToken jwt;

    @Inject
    UsuarioRepositorio usuarios;

    @Inject
    ActualizarPerfilUseCase actualizarPerfil;

    @GET
    @Path("/me")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Devuelve los datos del usuario autenticado")
    public Map<String, Object> me() {
        return respuesta(usuarioAutenticado());
    }

    @PUT
    @Path("/me")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Actualiza nombres, apellidos y telefono del usuario autenticado (CU01.5)")
    public Map<String, Object> actualizar(@Valid ActualizarPerfilRequest request) {
        Usuario actualizado = actualizarPerfil.actualizar(usuarioAutenticado().id(),
                new ActualizarPerfilCommand(request.nombres(), request.apellidos(),
                        request.telefono()));
        return respuesta(actualizado);
    }

    private Usuario usuarioAutenticado() {
        Long id = Long.valueOf(jwt.getSubject());
        return usuarios.buscarPorId(id)
                .orElseThrow(() -> new DomainException(ErrorCodes.NO_ENCONTRADO,
                        "El usuario no existe."));
    }

    private Map<String, Object> respuesta(Usuario usuario) {
        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("id", usuario.id());
        respuesta.put("correo", usuario.correo().valor());
        respuesta.put("nombres", usuario.nombres());
        respuesta.put("apellidos", usuario.apellidos());
        respuesta.put("telefono", usuario.telefono());
        respuesta.put("estado", usuario.estado().name());
        respuesta.put("correoVerificado", usuario.correoVerificado());
        respuesta.put("roles", usuario.roles());
        respuesta.put("tokenId", jwt.getTokenID());
        return respuesta;
    }
}
