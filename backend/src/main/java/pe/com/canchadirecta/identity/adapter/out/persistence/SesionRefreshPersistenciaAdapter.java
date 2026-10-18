package pe.com.canchadirecta.identity.adapter.out.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import pe.com.canchadirecta.identity.application.dto.SesionRefreshInfo;
import pe.com.canchadirecta.identity.application.port.out.SesionRefreshRepositorio;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/**
 * Persistencia de sesiones de refresco mediante SQL nativo.
 * Se usa SQL directo (no entidad JPA) porque la tabla emplea CHAR(64) para el
 * hash, tipo que no tiene un mapeo JPA directo en Hibernate y romperia la
 * validacion de esquema.
 */
@ApplicationScoped
public class SesionRefreshPersistenciaAdapter implements SesionRefreshRepositorio {

    private final EntityManager em;

    @Inject
    public SesionRefreshPersistenciaAdapter(EntityManager em) {
        this.em = em;
    }

    @Override
    @Transactional
    public void registrarSesion(Long idUsuario, String tokenHash, Instant emitidoEn, Instant expiraEn) {
        em.createNativeQuery("INSERT INTO sesion_refresh "
                        + "(id_usuario, token_hash, emitido_en, expira_en) "
                        + "VALUES (:idUsuario, :tokenHash, :emitidoEn, :expiraEn)")
                .setParameter("idUsuario", idUsuario.intValue())
                .setParameter("tokenHash", tokenHash)
                .setParameter("emitidoEn", aUtc(emitidoEn))
                .setParameter("expiraEn", aUtc(expiraEn))
                .executeUpdate();
    }

    @Override
    public Optional<SesionRefreshInfo> buscarPorHash(String tokenHash) {
        List<?> filas = em.createNativeQuery("SELECT id_sesion, id_usuario, expira_en, revocado_en "
                        + "FROM sesion_refresh WHERE token_hash = :tokenHash")
                .setParameter("tokenHash", tokenHash)
                .setMaxResults(1)
                .getResultList();
        if (filas.isEmpty()) {
            return Optional.empty();
        }
        Object[] fila = (Object[]) filas.get(0);
        return Optional.of(new SesionRefreshInfo(
                ((Number) fila[0]).longValue(),
                ((Number) fila[1]).longValue(),
                aInstant(fila[2]),
                fila[3] == null ? null : aInstant(fila[3])));
    }

    @Override
    @Transactional
    public void revocar(String tokenHash, Instant revocadoEn) {
        em.createNativeQuery("UPDATE sesion_refresh SET revocado_en = :revocadoEn "
                        + "WHERE token_hash = :tokenHash AND revocado_en IS NULL")
                .setParameter("revocadoEn", aUtc(revocadoEn))
                .setParameter("tokenHash", tokenHash)
                .executeUpdate();
    }

    @Override
    @Transactional
    public void revocarTodasDelUsuario(Long idUsuario, Instant revocadoEn) {
        em.createNativeQuery("UPDATE sesion_refresh SET revocado_en = :revocadoEn "
                        + "WHERE id_usuario = :idUsuario AND revocado_en IS NULL")
                .setParameter("revocadoEn", aUtc(revocadoEn))
                .setParameter("idUsuario", idUsuario.intValue())
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
