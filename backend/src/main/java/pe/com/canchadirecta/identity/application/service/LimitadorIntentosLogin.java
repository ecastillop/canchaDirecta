package pe.com.canchadirecta.identity.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import pe.com.canchadirecta.shared.application.port.out.Reloj;
import pe.com.canchadirecta.shared.domain.DomainException;
import pe.com.canchadirecta.shared.domain.ErrorCodes;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * S-4: limite de intentos fallidos de login por correo, en memoria y por instancia.
 *
 * <p>Suficiente para el despliegue de un solo nodo del proyecto. En un cluster habria que
 * mover el contador a un almacen compartido (Redis o tabla); se documenta en `SEGURIDAD.md`.</p>
 */
@ApplicationScoped
public class LimitadorIntentosLogin {

    private record Ventana(int fallos, Instant bloqueadoHasta) {
        Ventana conFallo() {
            return new Ventana(fallos + 1, bloqueadoHasta);
        }

        Ventana bloqueada(Instant hasta) {
            return new Ventana(0, hasta);
        }

        Ventana reiniciada() {
            return new Ventana(0, null);
        }
    }

    private final Map<String, Ventana> intentos = new ConcurrentHashMap<>();
    private final Reloj reloj;
    private final int maxIntentos;
    private final long bloqueoMinutos;

    @Inject
    public LimitadorIntentosLogin(Reloj reloj,
                                  @ConfigProperty(name = "cd.auth.login-max-intentos",
                                          defaultValue = "5") int maxIntentos,
                                  @ConfigProperty(name = "cd.auth.login-bloqueo-min",
                                          defaultValue = "5") long bloqueoMinutos) {
        this.reloj = reloj;
        this.maxIntentos = Math.max(1, maxIntentos);
        this.bloqueoMinutos = Math.max(1, bloqueoMinutos);
    }

    /** Lanza `429 TOO_MANY_ATTEMPTS` si el correo sigue bloqueado. */
    public void verificarPermitido(String correoTexto) {
        String clave = clave(correoTexto);
        Ventana actual = intentos.get(clave);
        if (actual == null) {
            return;
        }
        Instant ahora = reloj.ahora();
        synchronized (actual) {
            if (actual.bloqueadoHasta() != null && ahora.isBefore(actual.bloqueadoHasta())) {
                throw new DomainException(ErrorCodes.DEMASIADOS_INTENTOS,
                        "Demasiados intentos fallidos. Intenta de nuevo en unos minutos.");
            }
            if (actual.bloqueadoHasta() != null) {
                intentos.put(clave, actual.reiniciada());
            }
        }
    }

    public void registrarFallo(String correoTexto) {
        String clave = clave(correoTexto);
        intentos.compute(clave, (k, actual) -> {
            Ventana v = actual == null ? new Ventana(0, null) : actual;
            synchronized (v) {
                if (v.bloqueadoHasta() != null && reloj.ahora().isBefore(v.bloqueadoHasta())) {
                    return v;
                }
                Ventana conFallo = v.conFallo();
                if (conFallo.fallos() >= maxIntentos) {
                    return conFallo.bloqueada(reloj.ahora().plus(bloqueoMinutos, ChronoUnit.MINUTES));
                }
                return conFallo;
            }
        });
    }

    public void limpiar(String correoTexto) {
        intentos.remove(clave(correoTexto));
    }

    private static String clave(String correo) {
        return correo == null ? "" : correo.trim().toLowerCase(Locale.ROOT);
    }
}
