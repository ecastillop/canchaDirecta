package pe.com.canchadirecta.platform.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.com.canchadirecta.platform.application.port.in.ConfiguracionPlataformaUseCase;
import pe.com.canchadirecta.platform.application.port.out.ConfiguracionPlataformaRepositorio;
import pe.com.canchadirecta.platform.domain.model.ConfiguracionPlataformaAdmin;
import pe.com.canchadirecta.shared.application.port.out.RegistroAuditoria;
import pe.com.canchadirecta.shared.application.port.out.Reloj;
import pe.com.canchadirecta.shared.domain.DomainException;
import pe.com.canchadirecta.shared.domain.ErrorCodes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.regex.Pattern;

/**
 * Configuracion persistente de plataforma (ADR-0037).
 *
 * <p>Los cambios se validan, se guardan en `configuracion_plataforma` y dejan auditoria. Los
 * servicios que aplican comision, ventanas de cancelacion o anticipacion leen la tabla en
 * tiempo de ejecucion, por lo que el cambio rige desde la siguiente operacion.</p>
 */
@ApplicationScoped
public class ConfiguracionPlataformaService implements ConfiguracionPlataformaUseCase {

    private static final Pattern CORREO = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final long MAX_HORAS = 720;

    private final ConfiguracionPlataformaRepositorio repositorio;
    private final RegistroAuditoria auditoria;
    private final Reloj reloj;

    @Inject
    public ConfiguracionPlataformaService(ConfiguracionPlataformaRepositorio repositorio,
                                          RegistroAuditoria auditoria, Reloj reloj) {
        this.repositorio = repositorio;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    @Override
    public ConfiguracionPlataformaAdmin consultar() {
        return repositorio.cargar();
    }

    @Override
    @Transactional
    public ConfiguracionPlataformaAdmin actualizar(Long idAdmin, BigDecimal comisionPorcentaje,
                                                   Long horasCancelacionGratis, Long horasMinimasCancelacion,
                                                   Long diasAnticipacionMax, String correoSoporte) {
        if (comisionPorcentaje == null) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "La comision es obligatoria.");
        }
        if (comisionPorcentaje.signum() < 0 || comisionPorcentaje.compareTo(new BigDecimal("100")) > 0) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "La comision debe estar entre 0 y 100.");
        }
        long horasGratis = horas(horasCancelacionGratis, "Las horas de cancelacion gratuita");
        long horasMinimas = horas(horasMinimasCancelacion, "Las horas minimas de cancelacion");
        if (diasAnticipacionMax == null || diasAnticipacionMax < 1 || diasAnticipacionMax > 365) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS,
                    "Los dias de anticipacion deben estar entre 1 y 365.");
        }
        String correo = correoSoporte == null ? "" : correoSoporte.trim();
        if (correo.isEmpty() || correo.length() > 160 || !CORREO.matcher(correo).matches()) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS,
                    "El correo de soporte no es valido.");
        }

        ConfiguracionPlataformaAdmin anterior = repositorio.cargar();
        ConfiguracionPlataformaAdmin nueva = new ConfiguracionPlataformaAdmin(
                comisionPorcentaje.setScale(2, RoundingMode.HALF_UP),
                horasGratis, horasMinimas, diasAnticipacionMax, correo, null);
        Instant momento = reloj.ahora();
        ConfiguracionPlataformaAdmin guardada = repositorio.guardar(nueva, momento);

        auditoria.registrar(idAdmin, "CONFIGURACION_ACTUALIZADA", "configuracion_plataforma", "1",
                "comision=" + anterior.comisionPorcentaje() + "->" + guardada.comisionPorcentaje()
                        + " horasCancelacionGratis=" + anterior.horasCancelacionGratis() + "->"
                        + guardada.horasCancelacionGratis()
                        + " horasMinimasCancelacion=" + anterior.horasMinimasCancelacion() + "->"
                        + guardada.horasMinimasCancelacion()
                        + " diasAnticipacionMax=" + anterior.diasAnticipacionMax() + "->"
                        + guardada.diasAnticipacionMax()
                        + " correoSoporte=" + anterior.correoSoporte() + "->" + guardada.correoSoporte());
        return guardada;
    }

    private static long horas(Long valor, String etiqueta) {
        if (valor == null || valor < 0 || valor > MAX_HORAS) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS,
                    etiqueta + " deben estar entre 0 y " + MAX_HORAS + ".");
        }
        return valor;
    }
}
