-- CU03.2: clave de idempotencia por titular en la creacion de reservas.
-- MySQL permite varios NULL en un indice UNIQUE, asi que las reservas sin clave no colisionan.
ALTER TABLE reserva
  ADD COLUMN clave_idempotencia VARCHAR(80) NULL AFTER fecha_creacion,
  ADD CONSTRAINT uq_reserva_idempotencia UNIQUE (id_usuario, clave_idempotencia);
