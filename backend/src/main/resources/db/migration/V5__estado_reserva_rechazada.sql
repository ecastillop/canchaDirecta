-- V5: el propietario puede rechazar una reserva pendiente de su cancha.
--
-- El CHECK original (V1) solo admitia PENDIENTE/CONFIRMADA/CANCELADA/FINALIZADA.
-- 'RECHAZADA' es una extension minima justificada (ADR-0007, ADR-0018): un rechazo del
-- propietario no equivale a una cancelacion del titular (responsabilidad y politica
-- economica distintas, ver L-12), y sin un estado propio el listado del propietario no
-- podria distinguirlos.
ALTER TABLE reserva DROP CHECK ck_reserva_estado;

ALTER TABLE reserva
  ADD CONSTRAINT ck_reserva_estado
  CHECK (estado IN ('PENDIENTE','CONFIRMADA','CANCELADA','RECHAZADA','FINALIZADA'));
