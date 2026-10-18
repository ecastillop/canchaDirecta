package pe.com.canchadirecta.identity.adapter.out.security;

import jakarta.enterprise.context.ApplicationScoped;
import pe.com.canchadirecta.identity.application.port.out.HashTokens;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Hash SHA-256 en hexadecimal para tokens opacos. */
@ApplicationScoped
public class Sha256HashTokens implements HashTokens {

    @Override
    public String sha256Hex(String valor) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(valor.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
