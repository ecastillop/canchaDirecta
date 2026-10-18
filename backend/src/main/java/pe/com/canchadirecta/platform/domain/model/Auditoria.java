package pe.com.canchadirecta.platform.domain.model;

import java.time.Instant;

/**
 * Entrada del registro de auditoria (CU06.7).
 */
public record Auditoria(
        Long id,
        Long idUsuario,
        String accion,
        String entidad,
        String idEntidad,
        String detalle,
        Instant fechaEvento
) {
}
