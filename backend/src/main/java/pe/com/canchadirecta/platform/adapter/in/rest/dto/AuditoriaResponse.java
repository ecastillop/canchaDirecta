package pe.com.canchadirecta.platform.adapter.in.rest.dto;

import pe.com.canchadirecta.platform.domain.model.Auditoria;

import java.time.Instant;

/**
 * Entrada del registro de auditoria (CU06.7).
 */
public record AuditoriaResponse(
        Long id,
        Long idUsuario,
        String accion,
        String entidad,
        String idEntidad,
        String detalle,
        Instant fechaEvento
) {

    public static AuditoriaResponse de(Auditoria auditoria) {
        return new AuditoriaResponse(auditoria.id(), auditoria.idUsuario(), auditoria.accion(),
                auditoria.entidad(), auditoria.idEntidad(), auditoria.detalle(), auditoria.fechaEvento());
    }
}
