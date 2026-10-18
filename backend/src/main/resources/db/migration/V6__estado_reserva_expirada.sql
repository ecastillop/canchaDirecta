-- V6: una solicitud PENDIENTE sin pago aprobado expira al vencer el hold de pago.
--
-- El CHECK vigente (V5) admitia PENDIENTE/CONFIRMADA/CANCELADA/RECHAZADA/FINALIZADA.
-- 'EXPIRADA' es una extension minima justificada (ADR-0007, ADR-0022): una solicitud que
-- nadie paga ni atiende dentro de `config_establecimiento.hold_pago_min` no equivale a una
-- cancelacion del titular (ADR-0016) ni a un rechazo del propietario (ADR-0018): la
-- responsabilidad es del titular que no completo el pago, y el listado debe poder
-- distinguirla. La franja se libera igual que en los otros estados terminales.
ALTER TABLE reserva DROP CHECK ck_reserva_estado;

ALTER TABLE reserva
  ADD CONSTRAINT ck_reserva_estado
  CHECK (estado IN ('PENDIENTE','CONFIRMADA','CANCELADA','RECHAZADA','EXPIRADA','FINALIZADA'));
