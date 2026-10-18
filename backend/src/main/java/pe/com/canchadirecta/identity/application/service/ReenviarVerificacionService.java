package pe.com.canchadirecta.identity.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import pe.com.canchadirecta.identity.application.port.in.ReenviarVerificacionUseCase;
import pe.com.canchadirecta.identity.application.port.out.AuthTokenRepositorio;
import pe.com.canchadirecta.identity.application.port.out.GeneradorTokenOpaco;
import pe.com.canchadirecta.identity.application.port.out.HashTokens;
import pe.com.canchadirecta.identity.application.port.out.NotificadorCuenta;
import pe.com.canchadirecta.identity.application.port.out.UsuarioRepositorio;
import pe.com.canchadirecta.identity.domain.model.CorreoElectronico;
import pe.com.canchadirecta.identity.domain.model.TipoToken;
import pe.com.canchadirecta.identity.domain.model.Usuario;
import pe.com.canchadirecta.shared.application.port.out.Reloj;
import pe.com.canchadirecta.shared.domain.DomainException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/** Reemite el token de verificacion sin revelar el estado de la cuenta (CU01.2). */
@ApplicationScoped
public class ReenviarVerificacionService implements ReenviarVerificacionUseCase {

    private final UsuarioRepositorio usuarios;
    private final AuthTokenRepositorio tokens;
    private final GeneradorTokenOpaco generador;
    private final HashTokens hashTokens;
    private final NotificadorCuenta notificador;
    private final Reloj reloj;

    @ConfigProperty(name = "cd.auth.verify-minutes", defaultValue = "1440")
    long vigenciaMinutos;

    @Inject
    public ReenviarVerificacionService(UsuarioRepositorio usuarios, AuthTokenRepositorio tokens,
                                       GeneradorTokenOpaco generador, HashTokens hashTokens,
                                       NotificadorCuenta notificador, Reloj reloj) {
        this.usuarios = usuarios;
        this.tokens = tokens;
        this.generador = generador;
        this.hashTokens = hashTokens;
        this.notificador = notificador;
        this.reloj = reloj;
    }

    @Override
    public void reenviar(String correoTexto) {
        Optional<Usuario> encontrado = buscar(correoTexto);
        if (encontrado.isEmpty() || !encontrado.get().estaActivo()
                || encontrado.get().correoVerificado()) {
            return; // silencio deliberado
        }
        Usuario usuario = encontrado.get();
        String tokenEnClaro = generador.generar();
        Instant expiraEn = reloj.ahora().plus(vigenciaMinutos, ChronoUnit.MINUTES);
        tokens.emitir(usuario.id(), TipoToken.EMAIL_VERIFY,
                hashTokens.sha256Hex(tokenEnClaro), expiraEn);
        notificador.enviarVerificacionCorreo(usuario.correo().valor(), tokenEnClaro, expiraEn);
    }

    private Optional<Usuario> buscar(String correoTexto) {
        try {
            return usuarios.buscarPorCorreo(new CorreoElectronico(correoTexto));
        } catch (DomainException e) {
            return Optional.empty();
        }
    }
}
