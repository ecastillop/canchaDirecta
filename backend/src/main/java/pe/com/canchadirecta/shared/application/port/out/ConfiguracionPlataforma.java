package pe.com.canchadirecta.shared.application.port.out;

import java.math.BigDecimal;

/**
 * Configuracion de plataforma leida en tiempo de ejecucion (ADR-0037).
 *
 * <p>Los valores se guardan en `configuracion_plataforma` y la administracion los edita desde
 * `/api/v1/admin/configuracion`. Los modulos que aplican reglas economicas o de agenda leen
 * este puerto en cada operacion, de modo que un cambio se refleja sin reiniciar el backend.</p>
 */
public interface ConfiguracionPlataforma {

    /** Comision de plataforma (%) aplicada en las liquidaciones. */
    BigDecimal comisionPorcentaje();

    /** Horas de antelacion para que una cancelacion del cliente genere reembolso. */
    long horasCancelacionGratis();

    /** Horas de antelacion minima para cancelar una reserva. */
    long horasMinimasCancelacion();

    /** Dias maximos de anticipacion para reservar o consultar disponibilidad. */
    long diasAnticipacionMax();

    /** Correo de soporte publicado a los usuarios. */
    String correoSoporte();
}
