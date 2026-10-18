package pe.com.canchadirecta.identity.adapter.out.security;

import jakarta.enterprise.context.ApplicationScoped;
import pe.com.canchadirecta.identity.application.port.out.GeneradorTokenOpaco;

import java.security.SecureRandom;
import java.util.Base64;

/** Genera tokens opacos de 48 bytes con `SecureRandom` (base64url, sin relleno). */
@ApplicationScoped
public class SecureRandomGeneradorTokenOpaco implements GeneradorTokenOpaco {

    private static final SecureRandom ALEATORIO = new SecureRandom();
    private static final int BYTES = 48;

    @Override
    public String generar() {
        byte[] bytes = new byte[BYTES];
        ALEATORIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
