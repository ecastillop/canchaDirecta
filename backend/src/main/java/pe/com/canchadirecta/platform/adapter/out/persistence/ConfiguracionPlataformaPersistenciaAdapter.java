package pe.com.canchadirecta.platform.adapter.out.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import pe.com.canchadirecta.platform.application.port.out.ConfiguracionPlataformaRepositorio;
import pe.com.canchadirecta.platform.domain.model.ConfiguracionPlataformaAdmin;
import pe.com.canchadirecta.shared.adapter.out.persistence.SqlUtc;
import pe.com.canchadirecta.shared.application.port.out.ConfiguracionPlataforma;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lee y escribe `configuracion_plataforma` (ADR-0037).
 *
 * <p>La tabla es clave/valor con filas sembradas por Flyway; si una fila faltara se usa el
 * valor por defecto historico del proyecto, de modo que la plataforma sigue operando.</p>
 */
@ApplicationScoped
public class ConfiguracionPlataformaPersistenciaAdapter
        implements ConfiguracionPlataformaRepositorio, ConfiguracionPlataforma {

    private static final String COMISION = "comision_porcentaje";
    private static final String HORAS_GRATIS = "horas_cancelacion_gratis";
    private static final String HORAS_MINIMAS = "horas_minimas_cancelacion";
    private static final String DIAS_ANTICIPACION = "dias_anticipacion_max";
    private static final String CORREO = "correo_soporte";

    private static final BigDecimal COMISION_DEFECTO = new BigDecimal("10.00");
    private static final long HORAS_GRATIS_DEFECTO = 24;
    private static final long HORAS_MINIMAS_DEFECTO = 2;
    private static final long DIAS_ANTICIPACION_DEFECTO = 365;
    private static final String CORREO_DEFECTO = "soporte@canchadirecta.pe";

    private static final Map<String, String> DESCRIPCIONES = Map.of(
            COMISION, "Comision de plataforma (%) descontada al liquidar las reservas pagadas.",
            HORAS_GRATIS, "Horas de antelacion minima para que la cancelacion del cliente genere reembolso total.",
            HORAS_MINIMAS, "Horas de antelacion minima para que el cliente pueda cancelar su reserva.",
            DIAS_ANTICIPACION, "Dias maximos de anticipacion con los que se puede reservar o consultar disponibilidad.",
            CORREO, "Correo de contacto publicado para consultas de soporte.");

    private final EntityManager em;

    @Inject
    public ConfiguracionPlataformaPersistenciaAdapter(EntityManager em) {
        this.em = em;
    }

    @Override
    public ConfiguracionPlataformaAdmin cargar() {
        Map<String, String> valores = valores();
        return new ConfiguracionPlataformaAdmin(
                decimal(valores.get(COMISION), COMISION_DEFECTO),
                entero(valores.get(HORAS_GRATIS), HORAS_GRATIS_DEFECTO),
                entero(valores.get(HORAS_MINIMAS), HORAS_MINIMAS_DEFECTO),
                entero(valores.get(DIAS_ANTICIPACION), DIAS_ANTICIPACION_DEFECTO),
                texto(valores.get(CORREO), CORREO_DEFECTO),
                fechaActualizacion());
    }

    @Override
    public ConfiguracionPlataformaAdmin guardar(ConfiguracionPlataformaAdmin configuracion, Instant momento) {
        String fecha = SqlUtc.aLiteral(momento);
        guardar(COMISION, configuracion.comisionPorcentaje().setScale(2, RoundingMode.HALF_UP).toPlainString(), fecha);
        guardar(HORAS_GRATIS, String.valueOf(configuracion.horasCancelacionGratis()), fecha);
        guardar(HORAS_MINIMAS, String.valueOf(configuracion.horasMinimasCancelacion()), fecha);
        guardar(DIAS_ANTICIPACION, String.valueOf(configuracion.diasAnticipacionMax()), fecha);
        guardar(CORREO, configuracion.correoSoporte(), fecha);
        return new ConfiguracionPlataformaAdmin(configuracion.comisionPorcentaje(),
                configuracion.horasCancelacionGratis(), configuracion.horasMinimasCancelacion(),
                configuracion.diasAnticipacionMax(), configuracion.correoSoporte(), momento);
    }

    @Override
    public BigDecimal comisionPorcentaje() {
        return cargar().comisionPorcentaje();
    }

    @Override
    public long horasCancelacionGratis() {
        return cargar().horasCancelacionGratis();
    }

    @Override
    public long horasMinimasCancelacion() {
        return cargar().horasMinimasCancelacion();
    }

    @Override
    public long diasAnticipacionMax() {
        return cargar().diasAnticipacionMax();
    }

    @Override
    public String correoSoporte() {
        return cargar().correoSoporte();
    }

    private void guardar(String clave, String valor, String fecha) {
        em.createNativeQuery("INSERT INTO configuracion_plataforma (clave, valor, descripcion, fecha_actualizacion) "
                        + "VALUES (:clave, :valor, :descripcion, :fecha) "
                        + "ON DUPLICATE KEY UPDATE valor = VALUES(valor), fecha_actualizacion = VALUES(fecha_actualizacion)")
                .setParameter("clave", clave)
                .setParameter("valor", valor)
                .setParameter("descripcion", DESCRIPCIONES.get(clave))
                .setParameter("fecha", fecha)
                .executeUpdate();
    }

    private Map<String, String> valores() {
        List<?> filas = em.createNativeQuery("SELECT clave, valor FROM configuracion_plataforma").getResultList();
        Map<String, String> valores = new HashMap<>(filas.size());
        for (Object fila : filas) {
            Object[] columnas = (Object[]) fila;
            valores.put(String.valueOf(columnas[0]), String.valueOf(columnas[1]));
        }
        return valores;
    }

    private Instant fechaActualizacion() {
        List<?> filas = em.createNativeQuery(
                        "SELECT CAST(MAX(fecha_actualizacion) AS CHAR) FROM configuracion_plataforma")
                .getResultList();
        if (filas.isEmpty() || filas.get(0) == null) {
            return null;
        }
        return SqlUtc.aInstant(filas.get(0));
    }

    private static BigDecimal decimal(String valor, BigDecimal defecto) {
        try {
            return new BigDecimal(valor).setScale(2, RoundingMode.HALF_UP);
        } catch (RuntimeException error) {
            return defecto;
        }
    }

    private static long entero(String valor, long defecto) {
        try {
            return Long.parseLong(valor);
        } catch (RuntimeException error) {
            return defecto;
        }
    }

    private static String texto(String valor, String defecto) {
        return valor == null || valor.isBlank() ? defecto : valor.trim();
    }
}
