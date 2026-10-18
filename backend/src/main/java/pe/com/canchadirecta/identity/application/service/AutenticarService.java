package pe.com.canchadirecta.identity.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import pe.com.canchadirecta.identity.application.dto.IniciarSesionCommand;
import pe.com.canchadirecta.identity.application.dto.TokenEmitidoDto;
import pe.com.canchadirecta.identity.application.port.in.AutenticarUseCase;
import pe.com.canchadirecta.identity.application.port.out.EmisorTokens;
import pe.com.canchadirecta.identity.application.port.out.PasswordHasher;
import pe.com.canchadirecta.identity.application.port.out.UsuarioRepositorio;
import pe.com.canchadirecta.identity.domain.model.CorreoElectronico;
import pe.com.canchadirecta.identity.domain.model.Usuario;
import pe.com.canchadirecta.shared.domain.DomainException;
import pe.com.canchadirecta.shared.domain.ErrorCodes;

/**
 * Servicio de aplicacion: autenticacion por correo y contrasena.
 * Devuelve el mismo error para correo inexistente y contrasena erronea
 * (no filtra que cuentas existen).
 */
@ApplicationScoped
public class AutenticarService implements AutenticarUseCase {

    private final UsuarioRepositorio repositorio;
    private final PasswordHasher hasher;
    private final EmisorTokens emisorTokens;
    private final LimitadorIntentosLogin limitador;

    /**
     * Politica de verificacion de correo (CU01.2). Desactivada por defecto para el demo
     * (el envio de correo es simulado); en un despliegue real se activa para bloquear
     * el acceso de cuentas sin verificar.
     */
    @ConfigProperty(name = "cd.auth.requerir-correo-verificado", defaultValue = "false")
    boolean requerirCorreoVerificado;

    @Inject
    public AutenticarService(UsuarioRepositorio repositorio, PasswordHasher hasher,
                             EmisorTokens emisorTokens, LimitadorIntentosLogin limitador) {
        this.repositorio = repositorio;
        this.hasher = hasher;
        this.emisorTokens = emisorTokens;
        this.limitador = limitador;
    }

    @Override
    public TokenEmitidoDto autenticar(IniciarSesionCommand command) {
        limitador.verificarPermitido(command.correo());
        if (command.password() == null || command.password().isBlank()) {
            limitador.registrarFallo(command.correo());
            throw new DomainException(ErrorCodes.CREDENCIALES_INVALIDAS, "Credenciales invalidas.");
        }
        CorreoElectronico correo = new CorreoElectronico(command.correo());

        Usuario usuario = repositorio.buscarPorCorreo(correo)
                .orElseThrow(() -> {
                    limitador.registrarFallo(command.correo());
                    return new DomainException(ErrorCodes.CREDENCIALES_INVALIDAS,
                            "Credenciales invalidas.");
                });

        if (!hasher.coincide(command.password(), usuario.claveHash().valor())) {
            limitador.registrarFallo(command.correo());
            throw new DomainException(ErrorCodes.CREDENCIALES_INVALIDAS, "Credenciales invalidas.");
        }
        if (!usuario.estaActivo()) {
            throw new DomainException(ErrorCodes.SIN_PERMISO, "La cuenta no esta activa.");
        }
        if (requerirCorreoVerificado && !usuario.correoVerificado()) {
            throw new DomainException(ErrorCodes.CUENTA_NO_VERIFICADA,
                    "Verifica tu correo antes de iniciar sesion.");
        }

        limitador.limpiar(command.correo());
        return emisorTokens.emitir(usuario);
    }
}
