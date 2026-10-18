-- Limites de canje para promociones/cupones. NULL significa sin limite.
-- El uso se cuenta con las reservas vigentes (no canceladas, rechazadas ni expiradas)
-- que referencian la promocion en `reserva.id_promocion`.
ALTER TABLE promocion
  ADD COLUMN limite_usos INT NULL AFTER estado,
  ADD COLUMN limite_por_usuario INT NULL AFTER limite_usos,
  ADD CONSTRAINT ck_promocion_limite_usos CHECK (limite_usos IS NULL OR limite_usos > 0),
  ADD CONSTRAINT ck_promocion_limite_usuario CHECK (limite_por_usuario IS NULL OR limite_por_usuario > 0);
