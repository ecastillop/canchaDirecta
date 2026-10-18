package pe.com.canchadirecta.shared.adapter.in.rest;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import pe.com.canchadirecta.shared.domain.DomainException;
import pe.com.canchadirecta.shared.domain.ErrorCodes;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Traduce excepciones de dominio a respuestas RFC 9457 (Problem Details)
 * con un codigo de negocio estable. No expone stack traces.
 */
@Provider
public class DomainExceptionMapper implements ExceptionMapper<DomainException> {

    @Override
    public Response toResponse(DomainException ex) {
        int status = statusPara(ex.code());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", tituloPara(status));
        body.put("status", status);
        body.put("code", ex.code());
        body.put("detail", ex.getMessage());
        return Response.status(status)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(body)
                .build();
    }

    private int statusPara(String code) {
        return switch (code) {
            case ErrorCodes.CORREO_YA_REGISTRADO, ErrorCodes.CONFLICTO,
                 ErrorCodes.FRANJA_NO_DISPONIBLE -> 409;
            case ErrorCodes.CREDENCIALES_INVALIDAS, ErrorCodes.NO_AUTENTICADO -> 401;
            case ErrorCodes.SIN_PERMISO, ErrorCodes.CUENTA_NO_VERIFICADA -> 403;
            case ErrorCodes.NO_ENCONTRADO -> 404;
            case ErrorCodes.DEMASIADOS_INTENTOS -> 429;
            case ErrorCodes.DATOS_INVALIDOS -> 400;
            default -> 422;
        };
    }

    private String tituloPara(int status) {
        return switch (status) {
            case 400 -> "Solicitud invalida";
            case 401 -> "No autenticado";
            case 403 -> "Acceso denegado";
            case 404 -> "Recurso no encontrado";
            case 409 -> "Conflicto";
            case 422 -> "Entidad no procesable";
            default -> "Error";
        };
    }
}
