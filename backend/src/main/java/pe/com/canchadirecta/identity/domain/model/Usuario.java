package pe.com.canchadirecta.identity.domain.model;

import pe.com.canchadirecta.shared.domain.DomainException;
import pe.com.canchadirecta.shared.domain.ErrorCodes;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Agregado de dominio Usuario. Java puro: sin anotaciones de framework.
 */
public final class Usuario {

    private final Long id;
    private final String nombres;
    private final String apellidos;
    private final String telefono;
    private final CorreoElectronico correo;
    private final PasswordHash claveHash;
    private final EstadoUsuario estado;
    private final boolean correoVerificado;
    private final int tokenVersion;
    private final Instant fechaRegistro;
    private final Set<Rol> roles;

    private Usuario(Long id, String nombres, String apellidos, String telefono,
                    CorreoElectronico correo, PasswordHash claveHash, EstadoUsuario estado,
                    boolean correoVerificado, int tokenVersion, Instant fechaRegistro,
                    Set<Rol> roles) {
        this.id = id;
        this.nombres = nombres;
        this.apellidos = apellidos;
        this.telefono = telefono;
        this.correo = correo;
        this.claveHash = claveHash;
        this.estado = estado;
        this.correoVerificado = correoVerificado;
        this.tokenVersion = tokenVersion;
        this.fechaRegistro = fechaRegistro;
        this.roles = roles;
    }

    /** Alta de un nuevo usuario con el rol indicado; el correo queda pendiente de verificar. */
    public static Usuario registrar(String nombres, String apellidos, String telefono,
                                    CorreoElectronico correo, PasswordHash claveHash,
                                    Set<Rol> roles, Instant fechaRegistro) {
        String n = requerirTexto(nombres, "nombres", 80);
        String a = requerirTexto(apellidos, "apellidos", 80);
        String t = normalizarTelefono(telefono);
        Set<Rol> r = (roles == null || roles.isEmpty())
                ? EnumSet.of(Rol.USER)
                : EnumSet.copyOf(roles);
        return new Usuario(null, n, a, t, Objects.requireNonNull(correo),
                Objects.requireNonNull(claveHash), EstadoUsuario.ACTIVO, false, 0,
                Objects.requireNonNull(fechaRegistro), Collections.unmodifiableSet(r));
    }

    /** Reconstruccion desde persistencia. */
    public static Usuario reconstituir(Long id, String nombres, String apellidos, String telefono,
                                       CorreoElectronico correo, PasswordHash claveHash,
                                       EstadoUsuario estado, boolean correoVerificado,
                                       int tokenVersion, Instant fechaRegistro, Set<Rol> roles) {
        return new Usuario(id, nombres, apellidos, telefono, correo, claveHash, estado,
                correoVerificado, tokenVersion, fechaRegistro,
                Collections.unmodifiableSet(EnumSet.copyOf(roles)));
    }

    /** Marca el correo como verificado (CU01.2). */
    public Usuario conCorreoVerificado() {
        return new Usuario(id, nombres, apellidos, telefono, correo, claveHash, estado, true,
                tokenVersion, fechaRegistro, roles);
    }

    /** Datos editables del perfil (CU01.5): nombres, apellidos y telefono. */
    public Usuario conPerfil(String nuevosNombres, String nuevosApellidos, String nuevoTelefono) {
        return new Usuario(id, requerirTexto(nuevosNombres, "nombres", 80),
                requerirTexto(nuevosApellidos, "apellidos", 80),
                normalizarTelefono(nuevoTelefono), correo, claveHash, estado, correoVerificado,
                tokenVersion, fechaRegistro, roles);
    }

    private static String requerirTexto(String valor, String campo, int max) {
        if (valor == null || valor.isBlank()) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "El campo " + campo + " es obligatorio.");
        }
        String v = valor.trim();
        if (v.length() > max) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS,
                    "El campo " + campo + " supera la longitud maxima (" + max + ").");
        }
        return v;
    }

    private static String normalizarTelefono(String telefono) {
        if (telefono == null || telefono.isBlank()) {
            return null;
        }
        String t = telefono.trim();
        if (t.length() > 20) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "El telefono supera 20 caracteres.");
        }
        return t;
    }

    public boolean estaActivo() {
        return estado == EstadoUsuario.ACTIVO;
    }

    public Long id() { return id; }
    public String nombres() { return nombres; }
    public String apellidos() { return apellidos; }
    public String telefono() { return telefono; }
    public CorreoElectronico correo() { return correo; }
    public PasswordHash claveHash() { return claveHash; }
    public EstadoUsuario estado() { return estado; }
    public boolean correoVerificado() { return correoVerificado; }
    public int tokenVersion() { return tokenVersion; }
    public Instant fechaRegistro() { return fechaRegistro; }
    public Set<Rol> roles() { return roles; }
}
