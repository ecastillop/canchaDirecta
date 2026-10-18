-- ============================================================================
-- V3__integrity_and_extensions.sql
-- 1) Equivalentes MySQL de los índices únicos filtrados de SQL Server.
-- 2) Extensiones mínimas justificadas (auth, idempotencia, cambios de reserva,
--    configuración de establecimiento, seguimiento de reclamo, eventos de pago).
-- Trazabilidad: ADR-0006 (unicidad parcial), ADR-0007 (extensiones).
-- ============================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 1) Unicidad parcial: foto principal por cancha (SQL Server: WHERE principal=1)
--    Columna generada STORED -> NULL cuando no aplica; UNIQUE ignora NULL.
-- ---------------------------------------------------------------------------
ALTER TABLE foto_cancha
  ADD COLUMN id_cancha_principal INT
    GENERATED ALWAYS AS (CASE WHEN principal = 1 THEN id_cancha ELSE NULL END) STORED;
ALTER TABLE foto_cancha
  ADD CONSTRAINT uq_foto_cancha_principal UNIQUE (id_cancha_principal);

-- ---------------------------------------------------------------------------
-- 2) Unicidad parcial: un solo pago APROBADO por reserva
--    (SQL Server: WHERE estado = 'APROBADO')
-- ---------------------------------------------------------------------------
ALTER TABLE pago
  ADD COLUMN id_reserva_aprobada BIGINT
    GENERATED ALWAYS AS (CASE WHEN estado = 'APROBADO' THEN id_reserva ELSE NULL END) STORED;
ALTER TABLE pago
  ADD CONSTRAINT uq_pago_aprobado_reserva UNIQUE (id_reserva_aprobada);

-- ---------------------------------------------------------------------------
-- 3) Tokens de verificación de correo y recuperación de contraseña (hasheados)
-- ---------------------------------------------------------------------------
CREATE TABLE auth_token (
  id_token   BIGINT       NOT NULL AUTO_INCREMENT,
  id_usuario INT          NOT NULL,
  tipo       VARCHAR(30)  NOT NULL,            -- EMAIL_VERIFY | PASSWORD_RESET
  token_hash CHAR(64)     NOT NULL,            -- SHA-256 hex del token en claro
  expira_en  DATETIME(6)  NOT NULL,
  usado_en   DATETIME(6)  NULL,
  fecha_emision DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id_token),
  CONSTRAINT uq_auth_token_hash UNIQUE (token_hash),
  CONSTRAINT fk_auth_token_usuario FOREIGN KEY (id_usuario) REFERENCES usuario(id_usuario),
  CONSTRAINT ck_auth_token_tipo CHECK (tipo IN ('EMAIL_VERIFY','PASSWORD_RESET'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------------
-- 4) Sesiones / refresh tokens rotativos y revocables
-- ---------------------------------------------------------------------------
CREATE TABLE sesion_refresh (
  id_sesion    BIGINT       NOT NULL AUTO_INCREMENT,
  id_usuario   INT          NOT NULL,
  token_hash   CHAR(64)     NOT NULL,
  emitido_en   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  expira_en    DATETIME(6)  NOT NULL,
  revocado_en  DATETIME(6)  NULL,
  user_agent   VARCHAR(255) NULL,
  ip_origen    VARCHAR(45)  NULL,
  PRIMARY KEY (id_sesion),
  CONSTRAINT uq_sesion_refresh_hash UNIQUE (token_hash),
  CONSTRAINT fk_sesion_refresh_usuario FOREIGN KEY (id_usuario) REFERENCES usuario(id_usuario)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------------
-- 5) Claves de idempotencia para operaciones de reserva/pago
-- ---------------------------------------------------------------------------
CREATE TABLE idempotencia (
  id_idempotencia BIGINT       NOT NULL AUTO_INCREMENT,
  clave           VARCHAR(120) NOT NULL,
  id_usuario      INT          NULL,
  operacion       VARCHAR(60)  NOT NULL,
  resultado_ref   VARCHAR(120) NULL,  -- id de recurso resultante (p.ej. id_reserva)
  fecha_creacion  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id_idempotencia),
  CONSTRAINT uq_idempotencia_clave_operacion UNIQUE (clave, operacion),
  CONSTRAINT fk_idempotencia_usuario FOREIGN KEY (id_usuario) REFERENCES usuario(id_usuario)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------------
-- 6) Solicitudes de cambio de reserva con ciclo explícito
-- ---------------------------------------------------------------------------
CREATE TABLE solicitud_cambio_reserva (
  id_solicitud   BIGINT        NOT NULL AUTO_INCREMENT,
  id_reserva     BIGINT        NOT NULL,
  id_usuario     INT           NOT NULL,
  nuevo_inicio   DATETIME(6)   NOT NULL,
  nuevo_fin      DATETIME(6)   NOT NULL,
  estado         VARCHAR(20)   NOT NULL DEFAULT 'SOLICITADA',
  motivo         VARCHAR(250)  NULL,
  fecha_solicitud DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  fecha_resolucion DATETIME(6) NULL,
  PRIMARY KEY (id_solicitud),
  CONSTRAINT fk_solicitud_cambio_reserva FOREIGN KEY (id_reserva) REFERENCES reserva(id_reserva),
  CONSTRAINT fk_solicitud_cambio_usuario FOREIGN KEY (id_usuario) REFERENCES usuario(id_usuario),
  CONSTRAINT ck_solicitud_cambio_rango  CHECK (nuevo_inicio < nuevo_fin),
  CONSTRAINT ck_solicitud_cambio_estado CHECK (estado IN ('SOLICITADA','APROBADA','RECHAZADA','CANCELADA'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------------
-- 7) Políticas configurables por establecimiento (defaults propuestos)
-- ---------------------------------------------------------------------------
CREATE TABLE config_establecimiento (
  id_establecimiento          INT          NOT NULL,
  duracion_slot_min           INT          NOT NULL DEFAULT 30,
  duracion_min_reserva_min    INT          NOT NULL DEFAULT 30,
  duracion_max_reserva_min    INT          NOT NULL DEFAULT 180,
  hold_pago_min               INT          NOT NULL DEFAULT 10,
  horas_cancelacion_gratis    INT          NOT NULL DEFAULT 24,
  dias_anticipacion_max       INT          NOT NULL DEFAULT 7,
  zona_horaria                VARCHAR(60)  NOT NULL DEFAULT 'America/Lima',
  fecha_actualizacion         DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id_establecimiento),
  CONSTRAINT fk_config_establecimiento FOREIGN KEY (id_establecimiento) REFERENCES establecimiento(id_establecimiento),
  CONSTRAINT ck_config_duraciones CHECK (duracion_slot_min > 0 AND duracion_min_reserva_min > 0
    AND duracion_max_reserva_min >= duracion_min_reserva_min),
  CONSTRAINT ck_config_hold      CHECK (hold_pago_min >= 0),
  CONSTRAINT ck_config_cancel    CHECK (horas_cancelacion_gratis >= 0),
  CONSTRAINT ck_config_anticip   CHECK (dias_anticipacion_max > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------------
-- 8) Seguimiento de reclamos (comentarios/resolución)
-- ---------------------------------------------------------------------------
CREATE TABLE reclamo_seguimiento (
  id_seguimiento BIGINT       NOT NULL AUTO_INCREMENT,
  id_reclamo     BIGINT       NOT NULL,
  id_usuario     INT          NULL,
  comentario     VARCHAR(800) NOT NULL,
  fecha          DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id_seguimiento),
  CONSTRAINT fk_reclamo_seguimiento_reclamo FOREIGN KEY (id_reclamo) REFERENCES reclamo(id_reclamo),
  CONSTRAINT fk_reclamo_seguimiento_usuario FOREIGN KEY (id_usuario) REFERENCES usuario(id_usuario)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------------
-- 9) Eventos de pago simulados (trazabilidad/idempotencia de resultados)
-- ---------------------------------------------------------------------------
CREATE TABLE pago_evento (
  id_evento      BIGINT       NOT NULL AUTO_INCREMENT,
  id_pago        BIGINT       NULL,
  id_reserva     BIGINT       NOT NULL,
  tipo           VARCHAR(30)  NOT NULL,  -- SOLICITADO | APROBADO | RECHAZADO | REEMBOLSADO
  referencia_externa VARCHAR(80) NULL,
  detalle        VARCHAR(300) NULL,
  fecha_evento   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id_evento),
  CONSTRAINT fk_pago_evento_reserva FOREIGN KEY (id_reserva) REFERENCES reserva(id_reserva),
  CONSTRAINT fk_pago_evento_pago    FOREIGN KEY (id_pago)    REFERENCES pago(id_pago),
  CONSTRAINT ck_pago_evento_tipo CHECK (tipo IN ('SOLICITADO','APROBADO','RECHAZADO','REEMBOLSADO'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Índices de apoyo para disponibilidad y expiración
CREATE INDEX ix_reserva_estado_inicio ON reserva (estado, inicio, fin);
CREATE INDEX ix_auth_token_usuario_tipo ON auth_token (id_usuario, tipo, expira_en);
CREATE INDEX ix_sesion_refresh_usuario ON sesion_refresh (id_usuario, expira_en);
CREATE INDEX ix_pago_evento_reserva ON pago_evento (id_reserva, tipo);
