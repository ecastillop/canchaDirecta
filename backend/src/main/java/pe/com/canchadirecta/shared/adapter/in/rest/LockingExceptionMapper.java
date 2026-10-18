package pe.com.canchadirecta.shared.adapter.in.rest;

import jakarta.persistence.PessimisticLockException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import pe.com.canchadirecta.shared.domain.ErrorCodes;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Traduce un conflicto de concurrencia transitorio (deadlock o timeout al tomar el
 * bloqueo de una fila con `SELECT ... FOR UPDATE`) a una respuesta controlada `409`
 * con el codigo estable {@link ErrorCodes#RECURSO_BLOQUEADO} (ver prompt maestro 05.3.10).
 *
 * <p>Sin este mapeo, MySQL/Quarkus devolveria un `500` opaco; con el, el cliente puede
 * reintentar la operacion. La operacion se revierte por completo (la transaccion ya esta
 * marcada para rollback), de modo que no quedan escrituras a medias.</p>
 */
@Provider
public class LockingExceptionMapper implements ExceptionMapper<PessimisticLockException> {

    @Override
    public Response toResponse(PessimisticLockException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", "Conflicto");
        body.put("status", 409);
        body.put("code", ErrorCodes.RECURSO_BLOQUEADO);
        body.put("detail", "El recurso esta siendo modificado por otra operacion; vuelva a intentarlo.");
        return Response.status(409)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(body)
                .build();
    }
}
