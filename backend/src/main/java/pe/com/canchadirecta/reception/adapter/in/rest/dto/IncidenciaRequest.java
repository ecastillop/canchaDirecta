package pe.com.canchadirecta.reception.adapter.in.rest.dto;

/** Incidencia operativa registrada por recepcion. */
public record IncidenciaRequest(String asunto, String descripcion, Long idReserva) {
}
