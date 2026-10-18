package pe.com.canchadirecta.reception.application.port.in;

import pe.com.canchadirecta.reception.application.dto.OperadorAsignado;

import java.util.List;

/** Personal y permisos de recepcion por sede (solo PLATFORM_ADMIN). */
public interface RecepcionAdminUseCase {

    List<OperadorAsignado> operadores(Long idEstablecimiento);

    void asignar(Long idAdmin, Long idEstablecimiento, Long idUsuario, String cargo);

    void remover(Long idAdmin, Long idEstablecimiento, Long idUsuario);
}
