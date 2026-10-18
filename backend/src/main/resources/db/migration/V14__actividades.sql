-- Actividades publicadas por un establecimiento (torneos, clases, partidos abiertos)
-- y las inscripciones de los clientes. El cupo se controla contra las inscripciones
-- CONFIRMADA con bloqueo de fila en la capa de persistencia.
CREATE TABLE actividad (
  id_actividad      BIGINT       NOT NULL AUTO_INCREMENT,
  id_establecimiento INT         NOT NULL,
  nombre            VARCHAR(150) NOT NULL,
  descripcion       VARCHAR(800) NULL,
  fecha_inicio      DATETIME(6)  NOT NULL,
  fecha_fin         DATETIME(6)  NOT NULL,
  cupos             INT          NOT NULL,
  precio            DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  estado            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVA',
  fecha_registro    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id_actividad),
  CONSTRAINT fk_actividad_establecimiento FOREIGN KEY (id_establecimiento)
    REFERENCES establecimiento(id_establecimiento),
  CONSTRAINT ck_actividad_estado CHECK (estado IN ('ACTIVA','CANCELADA')),
  CONSTRAINT ck_actividad_cupos CHECK (cupos > 0),
  CONSTRAINT ck_actividad_fechas CHECK (fecha_fin > fecha_inicio),
  CONSTRAINT ck_actividad_precio CHECK (precio >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE actividad_inscripcion (
  id_inscripcion BIGINT      NOT NULL AUTO_INCREMENT,
  id_actividad    BIGINT     NOT NULL,
  id_usuario      INT        NOT NULL,
  estado          VARCHAR(20) NOT NULL DEFAULT 'CONFIRMADA',
  fecha_registro  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id_inscripcion),
  CONSTRAINT uq_actividad_inscripcion UNIQUE (id_actividad, id_usuario),
  CONSTRAINT fk_actividad_inscripcion_actividad FOREIGN KEY (id_actividad)
    REFERENCES actividad(id_actividad),
  CONSTRAINT fk_actividad_inscripcion_usuario FOREIGN KEY (id_usuario)
    REFERENCES usuario(id_usuario),
  CONSTRAINT ck_actividad_inscripcion_estado CHECK (estado IN ('CONFIRMADA','CANCELADA'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_actividad_estado_fecha ON actividad (estado, fecha_inicio);
CREATE INDEX idx_inscripcion_usuario ON actividad_inscripcion (id_usuario, fecha_registro);
