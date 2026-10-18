package pe.com.canchadirecta.shared.adapter.in.rest;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import pe.com.canchadirecta.shared.domain.ErrorCodes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Traduce fallos de validacion de bean a RFC 9457 con la lista de campos invalidos. */
@Provider
public class ValidationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {

    @Override
    public Response toResponse(ConstraintViolationException ex) {
        List<Map<String, String>> errores = new ArrayList<>();
        for (ConstraintViolation<?> v : ex.getConstraintViolations()) {
            Map<String, String> e = new LinkedHashMap<>();
            e.put("campo", String.valueOf(v.getPropertyPath()));
            e.put("mensaje", v.getMessage());
            errores.add(e);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", "Solicitud invalida");
        body.put("status", 400);
        body.put("code", ErrorCodes.DATOS_INVALIDOS);
        body.put("detail", "Uno o mas campos no son validos.");
        body.put("errors", errores);
        return Response.status(Response.Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(body)
                .build();
    }
}
