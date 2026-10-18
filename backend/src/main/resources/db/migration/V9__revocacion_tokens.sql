-- L-20: instante a partir del cual los access tokens emitidos dejan de valer.
-- Se actualiza al cambiar roles/estado o la contrasena; el filtro compara el `iat` del JWT.
ALTER TABLE usuario
  ADD COLUMN tokens_invalidos_antes DATETIME(6) NULL AFTER correo_verificado;
