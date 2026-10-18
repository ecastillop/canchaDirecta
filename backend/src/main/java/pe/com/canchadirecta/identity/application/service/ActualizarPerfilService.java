package pe.com.canchadirecta.identity.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import pe.com.canchadirecta.identity.application.dto.ActualizarPerfilCommand;
import pe.com.canchadirecta.identity.application.port.in.ActualizarPerfilUseCase;
import pe.com.canchadirecta.identity.application.port.out.UsuarioRepositorio;
import pe.com.canchadirecta.identity.domain.model.Usuario;
import pe.com.canchadirecta.shared.domain.DomainException;
import pe.com.canchadirecta.shared.domain.ErrorCodes;

/** Actualiza el perfil propio respetando las validaciones del agregado (CU01.5). */
@ApplicationScoped
public class ActualizarPerfilService implements ActualizarPerfilUseCase {

    private final UsuarioRepositorio usuarios;

    @Inject
    public ActualizarPerfilService(UsuarioRepositorio usuarios) {
        this.usuarios = usuarios;
    }

    @Override
    public Usuario actualizar(Long idUsuario, ActualizarPerfilCommand command) {
        Usuario actual = usuarios.buscarPorId(idUsuario)
                .orElseThrow(() -> new DomainException(ErrorCodes.NO_ENCONTRADO,
                        "El usuario no existe."));
        Usuario actualizado = actual.conPerfil(command.nombres(), command.apellidos(),
                command.telefono());
        usuarios.actualizarPerfil(actualizado.id(), actualizado.nombres(),
                actualizado.apellidos(), actualizado.telefono());
        return actualizado;
    }
}
