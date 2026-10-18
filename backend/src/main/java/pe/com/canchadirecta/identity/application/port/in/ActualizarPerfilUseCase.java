package pe.com.canchadirecta.identity.application.port.in;

import pe.com.canchadirecta.identity.application.dto.ActualizarPerfilCommand;
import pe.com.canchadirecta.identity.domain.model.Usuario;

/** Edicion del perfil propio (CU01.5). */
public interface ActualizarPerfilUseCase {

    Usuario actualizar(Long idUsuario, ActualizarPerfilCommand command);
}
