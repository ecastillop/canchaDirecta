package pe.com.canchadirecta.shared.application.port.out;

/**
 * Registro de auditoria de acciones administrativas (tabla `auditoria`). Puerto compartido para
 * que cualquier modulo pueda dejar traza sin depender del modulo `platform`.
 */
public interface RegistroAuditoria {

    /**
     * Registra una accion.
     *
     * @param idUsuario administrador que la ejecuta (puede ser `null` en procesos del sistema)
     * @param accion    codigo corto, por ejemplo `PROPIETARIO_VALIDADO`
     * @param entidad   entidad afectada, por ejemplo `propietario`
     * @param idEntidad identificador de la entidad como texto
     * @param detalle   contexto libre (se recorta a 800 caracteres)
     */
    void registrar(Long idUsuario, String accion, String entidad, String idEntidad, String detalle);
}
