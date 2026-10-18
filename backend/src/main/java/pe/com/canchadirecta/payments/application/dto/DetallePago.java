package pe.com.canchadirecta.payments.application.dto;

import pe.com.canchadirecta.payments.domain.model.Pago;
import pe.com.canchadirecta.payments.domain.model.Reembolso;

import java.util.List;

/** Ultimo pago de una reserva junto con sus reembolsos (consulta del titular). */
public record DetallePago(Pago pago, List<Reembolso> reembolsos) {
}
