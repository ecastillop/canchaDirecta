package pe.com.canchadirecta.identity.application.port.out;

import pe.com.canchadirecta.identity.domain.model.CorreoElectronico;
import pe.com.canchadirecta.identity.domain.model.PasswordHash;
import pe.com.canchadirecta.identity.domain.model.Usuario;

import java.util.Optional;

/** Puerto de salida: persistencia de usuarios. */
public interface UsuarioRepositorio {

    boolean existePorCorreo(CorreoElectronico correo);

    Optional<Usuario> buscarPorCorreo(CorreoElectronico correo);

    Optional<Usuario> buscarPorId(Long id);

    Usuario guardar(Usuario usuario);

    /** Actualiza unicamente el hash de la contrasena (recuperacion de clave). */
    void actualizarClaveHash(Long id, PasswordHash nuevaClave);

    /** Marca el correo del usuario como verificado (CU01.2). */
    void marcarCorreoVerificado(Long id);

    /** Actualiza nombres, apellidos y telefono del perfil (CU01.5). */
    void actualizarPerfil(Long id, String nombres, String apellidos, String telefono);

    /** Corte de revocacion de access tokens del usuario (L-20). */
    int tokenVersion(Long id);

    /** Incrementa la version de token: invalida los access tokens ya emitidos (L-20). */
    void incrementarTokenVersion(Long id);
}
