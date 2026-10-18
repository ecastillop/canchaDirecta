package pe.com.canchadirecta.shared.application.port.out;

import java.time.Instant;

/** Puerto de salida: reloj (inyectable para pruebas deterministas). */
public interface Reloj {

    Instant ahora();
}
