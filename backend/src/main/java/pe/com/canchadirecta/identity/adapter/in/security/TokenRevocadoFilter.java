package pe.com.canchadirecta.identity.adapter.in.security;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.jwt.JsonWebToken;
import pe.com.canchadirecta.identity.application.port.out.UsuarioRepositorio;
import pe.com.canchadirecta.shared.domain.ErrorCodes;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * L-20: rechaza access tokens cuya version (`ver`) no coincide con `usuario.token_version`.
 * Cambiar roles, estado o contrasena incrementa la version y deja sin efecto los tokens ya
 * emitidos sin depender de la precision de `iat`.
 *
 * <p>Se ejecuta despues de la autenticacion, con el JWT ya poblado, y solo consulta la base
 * cuando hay un sujeto autenticado.</p>
 */
@Provider
@Priority(Priorities.AUTHORIZATION)
public class TokenRevocadoFilter implements ContainerRequestFilter {

    @Inject
    JsonWebToken jwt;

    @Inject
    UsuarioRepositorio usuarios;

    @Override
    public void filter(ContainerRequestContext request) {
        String subject = jwt.getSubject();
        if (subject == null || subject.isBlank()) {
            return;
        }
        Long id;
        try {
            id = Long.valueOf(subject);
        } catch (NumberFormatException ex) {
            return;
        }
        Object claim = jwt.getClaim("ver");
        int versionDelToken;
        try {
            versionDelToken = claim == null ? 0 : Integer.parseInt(String.valueOf(claim));
        } catch (NumberFormatException ex) {
            versionDelToken = 0;
        }
        if (usuarios.tokenVersion(id) != versionDelToken) {
            request.abortWith(revocada());
        }
    }

    private static Response revocada() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", "No autenticado");
        body.put("status", 401);
        body.put("code", ErrorCodes.NO_AUTENTICADO);
        body.put("detail", "La sesion fue revocada. Inicia sesion de nuevo.");
        return Response.status(Response.Status.UNAUTHORIZED)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(body)
                .build();
    }
}
