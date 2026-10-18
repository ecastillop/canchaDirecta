package pe.com.canchadirecta.identity.application.port.out;

/** Puerto de salida: hashing y verificacion de contrasenas. */
public interface PasswordHasher {

    String hash(String passwordEnClaro);

    boolean coincide(String passwordEnClaro, String hashAlmacenado);
}
