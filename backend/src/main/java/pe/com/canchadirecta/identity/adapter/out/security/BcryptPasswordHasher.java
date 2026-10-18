package pe.com.canchadirecta.identity.adapter.out.security;

import at.favre.lib.crypto.bcrypt.BCrypt;
import jakarta.enterprise.context.ApplicationScoped;
import pe.com.canchadirecta.identity.application.port.out.PasswordHasher;

/** Adaptador de hashing con bcrypt (coste configurable, sal incluida). */
@ApplicationScoped
public class BcryptPasswordHasher implements PasswordHasher {

    private static final int COSTE = 12;

    @Override
    public String hash(String passwordEnClaro) {
        return BCrypt.withDefaults().hashToString(COSTE, passwordEnClaro.toCharArray());
    }

    @Override
    public boolean coincide(String passwordEnClaro, String hashAlmacenado) {
        if (passwordEnClaro == null || hashAlmacenado == null) {
            return false;
        }
        return BCrypt.verifyer().verify(passwordEnClaro.toCharArray(), hashAlmacenado).verified;
    }
}
