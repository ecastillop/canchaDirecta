package pe.com.canchadirecta.identity.application.port.out;

/** Puerto de salida: hash de tokens opacos (nunca se guardan en claro). */
public interface HashTokens {

    String sha256Hex(String valor);
}
