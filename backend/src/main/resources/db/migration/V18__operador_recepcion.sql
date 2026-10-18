-- Recepcion / operador (Bloque D del delta, ADR-0038).
-- El rol OPERADOR da acceso al portal de recepcion; el vinculo con cada sede vive en
-- operador_establecimiento y define el alcance de sus consultas y acciones.
INSERT INTO rol (nombre)
SELECT 'OPERADOR' WHERE NOT EXISTS (SELECT 1 FROM rol WHERE nombre = 'OPERADOR');

CREATE TABLE operador_establecimiento (
  id_usuario         INT          NOT NULL,
  id_establecimiento INT          NOT NULL,
  cargo              VARCHAR(60)  NULL,
  activo             TINYINT(1)   NOT NULL DEFAULT 1,
  fecha_alta         DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id_usuario, id_establecimiento),
  CONSTRAINT fk_operador_est_usuario FOREIGN KEY (id_usuario) REFERENCES usuario(id_usuario),
  CONSTRAINT fk_operador_est_establecimiento FOREIGN KEY (id_establecimiento)
    REFERENCES establecimiento(id_establecimiento)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
