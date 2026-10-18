-- CU01.2: estado de verificacion del correo.
-- Los usuarios existentes se consideran verificados para no romper sesiones ni flujos previos;
-- las cuentas nuevas se insertan explicitamente con FALSE.
ALTER TABLE usuario
  ADD COLUMN correo_verificado BOOLEAN NOT NULL DEFAULT TRUE AFTER estado;
