package pe.com.canchadirecta.reception.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.com.canchadirecta.reception.application.dto.OperadorAsignado;
import pe.com.canchadirecta.reception.application.port.in.RecepcionAdminUseCase;
import pe.com.canchadirecta.reception.application.port.out.RecepcionRepositorio;
import pe.com.canchadirecta.shared.application.port.out.RegistroAuditoria;
import pe.com.canchadirecta.shared.domain.DomainException;
import pe.com.canchadirecta.shared.domain.ErrorCodes;

import java.util.List;

/** Personal y permisos de recepcion por sede (ADR-0038). */
@ApplicationScoped
public class RecepcionAdminService implements RecepcionAdminUseCase {

    private final RecepcionRepositorio recepcion;
    private final RegistroAuditoria auditoria;

    @Inject
    public RecepcionAdminService(RecepcionRepositorio recepcion, RegistroAuditoria auditoria) {
        this.recepcion = recepcion;
        this.auditoria = auditoria;
    }

    @Override
    public List<OperadorAsignado> operadores(Long idEstablecimiento) {
        if (idEstablecimiento == null || !recepcion.establecimientoExiste(idEstablecimiento)) {
            throw new DomainException(ErrorCodes.NO_ENCONTRADO, "El establecimiento no existe.");
        }
        return recepcion.operadoresDe(idEstablecimiento);
    }

    @Override
    @Transactional
    public void asignar(Long idAdmin, Long idEstablecimiento, Long idUsuario, String cargo) {
        if (idEstablecimiento == null || !recepcion.establecimientoExiste(idEstablecimiento)) {
            throw new DomainException(ErrorCodes.NO_ENCONTRADO, "El establecimiento no existe.");
        }
        if (idUsuario == null || !recepcion.usuarioExiste(idUsuario)) {
            throw new DomainException(ErrorCodes.NO_ENCONTRADO, "El usuario no existe.");
        }
        String etiqueta = cargo == null ? null : cargo.trim();
        if (etiqueta != null && etiqueta.length() > 60) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "El cargo no puede superar 60 caracteres.");
        }
        recepcion.asignarOperador(idEstablecimiento, idUsuario, etiqueta == null || etiqueta.isEmpty() ? null : etiqueta);
        auditoria.registrar(idAdmin, "OPERADOR_ASIGNADO", "establecimiento",
                String.valueOf(idEstablecimiento), "usuario=" + idUsuario + " cargo=" + etiqueta);
    }

    @Override
    @Transactional
    public void remover(Long idAdmin, Long idEstablecimiento, Long idUsuario) {
        if (idEstablecimiento == null || idUsuario == null) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS,
                    "El establecimiento y el usuario son obligatorios.");
        }
        recepcion.removerOperador(idEstablecimiento, idUsuario);
        auditoria.registrar(idAdmin, "OPERADOR_REMOVIDO", "establecimiento",
                String.valueOf(idEstablecimiento), "usuario=" + idUsuario);
    }
}
