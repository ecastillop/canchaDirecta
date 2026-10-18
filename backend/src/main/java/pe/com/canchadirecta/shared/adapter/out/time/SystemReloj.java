package pe.com.canchadirecta.shared.adapter.out.time;

import jakarta.enterprise.context.ApplicationScoped;
import pe.com.canchadirecta.shared.application.port.out.Reloj;

import java.time.Instant;

/** Adaptador del reloj del sistema (UTC). */
@ApplicationScoped
public class SystemReloj implements Reloj {

    @Override
    public Instant ahora() {
        return Instant.now();
    }
}
