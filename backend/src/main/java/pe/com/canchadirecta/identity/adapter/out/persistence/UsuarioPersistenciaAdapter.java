package pe.com.canchadirecta.identity.adapter.out.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import pe.com.canchadirecta.identity.application.port.out.UsuarioRepositorio;
import pe.com.canchadirecta.identity.domain.model.CorreoElectronico;
import pe.com.canchadirecta.identity.domain.model.EstadoUsuario;
import pe.com.canchadirecta.identity.domain.model.PasswordHash;
import pe.com.canchadirecta.identity.domain.model.Rol;
import pe.com.canchadirecta.identity.domain.model.Usuario;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Adaptador de persistencia (JPA/Hibernate) del agregado Usuario. */
@ApplicationScoped
public class UsuarioPersistenciaAdapter implements UsuarioRepositorio {

    private final EntityManager em;

    @Inject
    public UsuarioPersistenciaAdapter(EntityManager em) {
        this.em = em;
    }

    @Override
    public boolean existePorCorreo(CorreoElectronico correo) {
        Long total = em.createQuery(
                        "select count(u) from UsuarioEntity u where u.correo = :correo", Long.class)
                .setParameter("correo", correo.valor())
                .getSingleResult();
        return total != null && total > 0;
    }

    @Override
    public Optional<Usuario> buscarPorCorreo(CorreoElectronico correo) {
        List<UsuarioEntity> encontrados = em.createQuery(
                        "select u from UsuarioEntity u where u.correo = :correo", UsuarioEntity.class)
                .setParameter("correo", correo.valor())
                .setMaxResults(1)
                .getResultList();
        return encontrados.stream().findFirst().map(this::aDominio);
    }

    @Override
    public Optional<Usuario> buscarPorId(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        UsuarioEntity entidad = em.find(UsuarioEntity.class, id.intValue());
        return Optional.ofNullable(entidad).map(this::aDominio);
    }

    @Override
    @Transactional
    public Usuario guardar(Usuario usuario) {
        UsuarioEntity entidad = new UsuarioEntity();
        entidad.setNombres(usuario.nombres());
        entidad.setApellidos(usuario.apellidos());
        entidad.setCorreo(usuario.correo().valor());
        entidad.setTelefono(usuario.telefono());
        entidad.setClaveHash(usuario.claveHash().valor());
        entidad.setEstado(usuario.estado().name());
        entidad.setCorreoVerificado(usuario.correoVerificado());
        entidad.setTokenVersion(usuario.tokenVersion());
        entidad.setFechaRegistro(usuario.fechaRegistro());
        em.persist(entidad);
        em.flush();

        for (Rol rol : usuario.roles()) {
            em.createNativeQuery(
                            "INSERT INTO usuario_rol (id_usuario, id_rol) "
                                    + "SELECT :uid, id_rol FROM rol WHERE nombre = :rol")
                    .setParameter("uid", entidad.getId())
                    .setParameter("rol", rol.name())
                    .executeUpdate();
        }
        return aDominio(entidad);
    }

    @Override
    @Transactional
    public void actualizarClaveHash(Long id, PasswordHash nuevaClave) {
        em.createNativeQuery("UPDATE usuario SET clave_hash = :clave WHERE id_usuario = :id")
                .setParameter("clave", nuevaClave.valor())
                .setParameter("id", id.intValue())
                .executeUpdate();
    }

    @Override
    @Transactional
    public void marcarCorreoVerificado(Long id) {
        em.createNativeQuery("UPDATE usuario SET correo_verificado = TRUE WHERE id_usuario = :id")
                .setParameter("id", id.intValue())
                .executeUpdate();
    }

    @Override
    @Transactional
    public void actualizarPerfil(Long id, String nombres, String apellidos, String telefono) {
        em.createNativeQuery("UPDATE usuario SET nombres = :nombres, apellidos = :apellidos, "
                        + "telefono = :telefono WHERE id_usuario = :id")
                .setParameter("nombres", nombres)
                .setParameter("apellidos", apellidos)
                .setParameter("telefono", telefono)
                .setParameter("id", id.intValue())
                .executeUpdate();
    }

    @Override
    public int tokenVersion(Long id) {
        List<?> filas = em.createNativeQuery(
                        "SELECT token_version FROM usuario WHERE id_usuario = :id")
                .setParameter("id", id.intValue())
                .setMaxResults(1)
                .getResultList();
        return filas.isEmpty() || filas.get(0) == null ? 0 : ((Number) filas.get(0)).intValue();
    }

    @Override
    @Transactional
    public void incrementarTokenVersion(Long id) {
        em.createNativeQuery("UPDATE usuario SET token_version = token_version + 1 "
                        + "WHERE id_usuario = :id")
                .setParameter("id", id.intValue())
                .executeUpdate();
    }

    private Usuario aDominio(UsuarioEntity e) {
        return Usuario.reconstituir(
                e.getId() == null ? null : e.getId().longValue(),
                e.getNombres(),
                e.getApellidos(),
                e.getTelefono(),
                new CorreoElectronico(e.getCorreo()),
                new PasswordHash(e.getClaveHash()),
                EstadoUsuario.valueOf(e.getEstado()),
                e.isCorreoVerificado(),
                e.getTokenVersion(),
                e.getFechaRegistro(),
                leerRoles(e.getId()));
    }

    @SuppressWarnings("unchecked")
    private Set<Rol> leerRoles(Integer idUsuario) {
        List<String> nombres = em.createNativeQuery(
                        "SELECT r.nombre FROM rol r "
                                + "JOIN usuario_rol ur ON ur.id_rol = r.id_rol "
                                + "WHERE ur.id_usuario = :uid")
                .setParameter("uid", idUsuario)
                .getResultList();
        Set<Rol> roles = EnumSet.noneOf(Rol.class);
        for (String n : nombres) {
            roles.add(Rol.valueOf(n));
        }
        return roles;
    }
}
