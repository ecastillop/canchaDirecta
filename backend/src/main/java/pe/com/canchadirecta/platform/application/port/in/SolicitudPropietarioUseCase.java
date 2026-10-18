package pe.com.canchadirecta.platform.application.port.in;

import pe.com.canchadirecta.platform.domain.model.PropietarioAdmin;

import java.util.Optional;

/** Alta y consulta de la solicitud de propietario (CU01.6). */
public interface SolicitudPropietarioUseCase {

    /** Crea la solicitud PENDIENTE (o devuelve la existente si el documento coincide). */
    PropietarioAdmin solicitar(Long idUsuario, String documentoIdentidad);

    Optional<PropietarioAdmin> consultar(Long idUsuario);
}
