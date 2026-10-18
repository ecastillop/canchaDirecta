-- Favoritos del cliente por cancha (unico por usuario y cancha).
CREATE TABLE favorito (
  id_usuario     INT          NOT NULL,
  id_cancha      INT          NOT NULL,
  fecha_registro DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id_usuario, id_cancha),
  CONSTRAINT fk_favorito_usuario FOREIGN KEY (id_usuario) REFERENCES usuario(id_usuario),
  CONSTRAINT fk_favorito_cancha  FOREIGN KEY (id_cancha)  REFERENCES cancha(id_cancha)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
