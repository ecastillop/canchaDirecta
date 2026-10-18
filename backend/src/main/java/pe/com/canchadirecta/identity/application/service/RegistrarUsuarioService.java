package pe.com.canchadirecta.identity.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import pe.com.canchadirecta.identity.application.dto.RegistrarUsuarioCommand;
import pe.com.canchadirecta.identity.application.dto.UsuarioCreadoDto;
import pe.com.canchadirecta.identity.application.port.in.RegistrarUsuarioUseCase;
import pe.com.canchadirecta.identity.application.port.out.AuthTokenRepositorio;
import pe.com.canchadirecta.identity.application.port.out.GeneradorTokenOpaco;
import pe.com.canchadirecta.identity.application.port.out.HashTokens;
import pe.com.canchadirecta.identity.application.port.out.NotificadorCuenta;
import pe.com.canchadirecta.identity.application.port.out.PasswordHasher;
import pe.com.canchadirecta.shared.application.port.out.Reloj;
import pe.com.canchadirecta.identity.application.port.out.UsuarioRepositorio;
import pe.com.canchadirecta.identity.domain.model.CorreoElectronico;
import pe.com.canchadirecta.identity.domain.model.PasswordHash;
import pe.com.canchadirecta.identity.domain.model.Rol;
import pe.com.canchadirecta.identity.domain.model.TipoToken;
import pe.com.canchadirecta.identity.domain.model.Usuario;
import pe.com.canchadirecta.shared.domain.DomainException;
import pe.com.canchadirecta.shared.domain.ErrorCodes;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.stream.Collectors;

/**
 * Servicio de aplicacion: orquesta el registro de usuarios.
 * No conoce HTTP ni el framework de presentacion.
 */
@ApplicationScoped
public class RegistrarUsuarioService implements RegistrarUsuarioUseCase {

    private static final int LONGITUD_MINIMA_PASSWORD = 8;

    private final UsuarioRepositorio repositorio;
    private final PasswordHasher hasher;
    private final Reloj reloj;
    private final AuthTokenRepositorio tokens;
    private final GeneradorTokenOpaco generador;
    private final HashTokens hashTokens;
    private final NotificadorCuenta notificador;

    @ConfigProperty(name = "cd.auth.verify-minutes", defaultValue = "1440")
    long vigenciaVerificacionMinutos;

    @Inject
    public RegistrarUsuarioService(UsuarioRepositorio repositorio, PasswordHasher hasher, Reloj reloj,
                                   AuthTokenRepositorio tokens, GeneradorTokenOpaco generador,
                                   HashTokens hashTokens, NotificadorCuenta notificador) {
        this.repositorio = repositorio;
        this.hasher = hasher;
        this.reloj = reloj;
        this.tokens = tokens;
        this.generador = generador;
        this.hashTokens = hashTokens;
        this.notificador = notificador;
    }

    @Override
    public UsuarioCreadoDto registrar(RegistrarUsuarioCommand command) {
        asegurarPasswordValida(command);
        CorreoElectronico correo = new CorreoElectronico(command.correo());

        if (repositorio.existePorCorreo(correo)) {
            throw new DomainException(ErrorCodes.CORREO_YA_REGISTRADO,
                    "Ya existe una cuenta registrada con ese correo.");
        }

        PasswordHash hash = new PasswordHash(hasher.hash(command.password()));
        Usuario usuario = Usuario.registrar(
                command.nombres(), command.apellidos(), command.telefono(),
                correo, hash, EnumSet.of(Rol.USER), reloj.ahora());

        Usuario guardado = repositorio.guardar(usuario);

        emitirVerificacion(guardado);

        return new UsuarioCreadoDto(
                guardado.id(),
                guardado.nombres(),
                guardado.apellidos(),
                guardado.correo().valor(),
                guardado.estado().name(),
                guardado.roles().stream().map(Enum::name).collect(Collectors.toSet()));
    }

    private void emitirVerificacion(Usuario usuario) {
        String tokenEnClaro = generador.generar();
        Instant expiraEn = reloj.ahora().plus(vigenciaVerificacionMinutos, ChronoUnit.MINUTES);
        tokens.emitir(usuario.id(), TipoToken.EMAIL_VERIFY,
                hashTokens.sha256Hex(tokenEnClaro), expiraEn);
        notificador.enviarVerificacionCorreo(usuario.correo().valor(), tokenEnClaro, expiraEn);
    }

    private void asegurarPasswordValida(RegistrarUsuarioCommand command) {        String p = command.password();
        if (p == null || p.length() < LONGITUD_MINIMA_PASSWORD) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS,
                    "La contrasena debe tener al menos " + LONGITUD_MINIMA_PASSWORD + " caracteres.");
        }
        if (!p.equals(command.confirmacionPassword())) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS,
                    "La confirmacion de la contrasena no coincide.");
        }
    }
}
