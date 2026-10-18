package pe.com.canchadirecta.identity.adapter.out.security;

import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import pe.com.canchadirecta.identity.application.dto.TokenEmitidoDto;
import pe.com.canchadirecta.identity.application.port.out.EmisorTokens;
import pe.com.canchadirecta.identity.application.port.out.HashTokens;
import pe.com.canchadirecta.shared.application.port.out.Reloj;
import pe.com.canchadirecta.identity.application.port.out.SesionRefreshRepositorio;
import pe.com.canchadirecta.identity.domain.model.Usuario;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.stream.Collectors;

/**
 * Emisor de tokens. El acceso es un JWT firmado (HS256) cuya clave se resuelve
 * con `smallrye.jwt.sign.key.location`. El refresco es un valor opaco aleatorio
 * que solo se persiste hasheado (SHA-256).
 */
@ApplicationScoped
public class SmallryeJwtEmisorTokens implements EmisorTokens {

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final Reloj reloj;
    private final HashTokens hashTokens;
    private final SesionRefreshRepositorio sesiones;

    @ConfigProperty(name = "smallrye.jwt.new-token.lifespan", defaultValue = "1800")
    long vidaAccessTokenSegundos;

    @ConfigProperty(name = "cd.auth.refresh-days", defaultValue = "30")
    long vidaRefreshDias;

    @Inject
    public SmallryeJwtEmisorTokens(Reloj reloj, HashTokens hashTokens,
                                   SesionRefreshRepositorio sesiones) {
        this.reloj = reloj;
        this.hashTokens = hashTokens;
        this.sesiones = sesiones;
    }

    @Override
    public TokenEmitidoDto emitir(Usuario usuario) {
        String accessToken = Jwt.claims()
                .issuer("canchadirecta")
                .subject(String.valueOf(usuario.id()))
                .claim("correo", usuario.correo().valor())
                .claim("ver", usuario.tokenVersion())
                .groups(usuario.roles().stream().map(Enum::name).collect(Collectors.toSet()))
                .expiresIn(vidaAccessTokenSegundos)
                .sign();

        String refreshToken = generarOpaco();
        Instant ahora = reloj.ahora();
        sesiones.registrarSesion(
                usuario.id(),
                hashTokens.sha256Hex(refreshToken),
                ahora,
                ahora.plus(vidaRefreshDias, ChronoUnit.DAYS));

        return TokenEmitidoDto.bearer(accessToken, refreshToken, vidaAccessTokenSegundos);
    }

    private String generarOpaco() {
        byte[] bytes = new byte[48];
        ALEATORIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
