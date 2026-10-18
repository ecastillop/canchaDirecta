package pe.com.canchadirecta.identity.adapter.out.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import pe.com.canchadirecta.identity.application.dto.TokenAuthInfo;
import pe.com.canchadirecta.identity.application.port.out.AuthTokenRepositorio;
import pe.com.canchadirecta.identity.domain.model.TipoToken;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/**
 * Tokens de un solo uso mediante SQL nativo (la tabla usa CHAR(64) para el hash, sin
 * mapeo JPA directo). `emitir` invalida los tokens previos vigentes del mismo tipo,
 * de forma que solo el ultimo enlace enviado sirve.
 */
@ApplicationScoped
public class AuthTokenPersistenciaAdapter implements AuthTokenRepositorio {

    private final EntityManager em;

    @Inject
    public AuthTokenPersistenciaAdapter(EntityManager em) {
        this.em = em;
    }

    @Override
    @Transactional
    public void emitir(Long idUsuario, TipoToken tipo, String tokenHash, Instant expiraEn) {
        em.createNativeQuery("UPDATE auth_token SET usado_en = :ahora "
                        + "WHERE id_usuario = :idUsuario AND tipo = :tipo AND usado_en IS NULL")
                .setParameter("ahora", aUtc(Instant.now()))
                .setParameter("idUsuario", idUsuario.intValue())
                .setParameter("tipo", tipo.name())
                .executeUpdate();

        em.createNativeQuery("INSERT INTO auth_token (id_usuario, tipo, token_hash, expira_en) "
                        + "VALUES (:idUsuario, :tipo, :tokenHash, :expiraEn)")
                .setParameter("idUsuario", idUsuario.intValue())
                .setParameter("tipo", tipo.name())
                .setParameter("tokenHash", tokenHash)
                .setParameter("expiraEn", aUtc(expiraEn))
                .executeUpdate();
    }

    @Override
    public Optional<TokenAuthInfo> buscarPorHash(String tokenHash, TipoToken tipo) {
        List<?> filas = em.createNativeQuery("SELECT id_token, id_usuario, expira_en, usado_en "
                        + "FROM auth_token WHERE token_hash = :tokenHash AND tipo = :tipo")
                .setParameter("tokenHash", tokenHash)
                .setParameter("tipo", tipo.name())
                .setMaxResults(1)
                .getResultList();
        if (filas.isEmpty()) {
            return Optional.empty();
        }
        Object[] fila = (Object[]) filas.get(0);
        return Optional.of(new TokenAuthInfo(
                ((Number) fila[0]).longValue(),
                ((Number) fila[1]).longValue(),
                aInstant(fila[2]),
                fila[3] == null ? null : aInstant(fila[3])));
    }

    @Override
    @Transactional
    public void marcarUsado(Long idToken, Instant usadoEn) {
        em.createNativeQuery("UPDATE auth_token SET usado_en = :usadoEn "
                        + "WHERE id_token = :idToken AND usado_en IS NULL")
                .setParameter("usadoEn", aUtc(usadoEn))
                .setParameter("idToken", idToken)
                .executeUpdate();
    }

    private LocalDateTime aUtc(Instant instante) {
        return LocalDateTime.ofInstant(instante, ZoneOffset.UTC);
    }

    private Instant aInstant(Object valor) {
        if (valor instanceof Timestamp ts) {
            return ts.toInstant();
        }
        if (valor instanceof LocalDateTime ldt) {
            return ldt.toInstant(ZoneOffset.UTC);
        }
        if (valor instanceof Instant i) {
            return i;
        }
        throw new IllegalStateException("Tipo de fecha no soportado: "
                + (valor == null ? "null" : valor.getClass().getName()));
    }
}
