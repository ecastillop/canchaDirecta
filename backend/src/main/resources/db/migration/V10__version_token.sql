-- L-20 (v2): la revocacion usa una version de token en lugar de marcas de tiempo, porque
-- `iat` solo tiene precision de segundos y no distingue un token emitido justo antes del
-- cambio de uno emitido justo despues en el mismo segundo.
ALTER TABLE usuario
  DROP COLUMN tokens_invalidos_antes,
  ADD COLUMN token_version INT NOT NULL DEFAULT 0 AFTER correo_verificado;
