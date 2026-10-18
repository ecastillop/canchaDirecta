package pe.com.canchadirecta.reception.application.dto;

import java.time.Instant;

/** Incidencia operativa registrada por recepcion (tabla `reclamo`). */
public record IncidenciaRecepcion(Long id, String asunto, String descripcion, String estado,
                                  Instant fechaRegistro, Long idReserva) {
}
