-- ============================================================================
-- V1__baseline_schema.sql
-- Esquema base: las 28 tablas de la Entrega 02 (ZIP), traducidas de SQL Server
-- a MySQL 8 (InnoDB / utf8mb4). Trazabilidad: docs/MODELO_DATOS.md §2.
--
-- Convenciones:
--   * Instantes en DATETIME(6) (UTC en aplicación).
--   * bit   -> TINYINT(1); datetime2 -> DATETIME(6); tinyint -> TINYINT.
--   * Los índices únicos filtrados de SQL Server (WHERE principal=1 y
--     WHERE estado='APROBADO') NO tienen equivalente directo en MySQL; se
--     recrean en V3 con columnas generadas + UNIQUE (ver ADR-0006).
--   * Los triggers T-SQL de solapamiento/bloqueo NO se traducen: la garantía
--     se implementa en el caso de uso con SELECT ... FOR UPDATE (ver ADR-0005).
-- ============================================================================

SET NAMES utf8mb4;

-- ------------------------------ Identidad ----------------------------------
CREATE TABLE distrito (
  id_distrito  INT          NOT NULL AUTO_INCREMENT,
  nombre       VARCHAR(80)  NOT NULL,
  PRIMARY KEY (id_distrito),
  CONSTRAINT uq_distrito_nombre UNIQUE (nombre)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE usuario (
  id_usuario     INT          NOT NULL AUTO_INCREMENT,
  nombres        VARCHAR(80)  NOT NULL,
  apellidos      VARCHAR(80)  NOT NULL,
  correo         VARCHAR(150) NOT NULL,
  telefono       VARCHAR(20)  NULL,
  clave_hash     VARCHAR(255) NOT NULL,
  estado         VARCHAR(20)  NOT NULL DEFAULT 'ACTIVO',
  fecha_registro DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id_usuario),
  -- utf8mb4_0900_ai_ci es case-insensitive => unicidad de correo sin distinguir mayúsculas
  CONSTRAINT uq_usuario_correo UNIQUE (correo),
  CONSTRAINT ck_usuario_estado CHECK (estado IN ('ACTIVO','INACTIVO','BLOQUEADO'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE rol (
  id_rol INT         NOT NULL AUTO_INCREMENT,
  nombre VARCHAR(30) NOT NULL,
  PRIMARY KEY (id_rol),
  CONSTRAINT uq_rol_nombre UNIQUE (nombre)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE usuario_rol (
  id_usuario INT NOT NULL,
  id_rol     INT NOT NULL,
  PRIMARY KEY (id_usuario, id_rol),
  CONSTRAINT fk_usuario_rol_usuario FOREIGN KEY (id_usuario) REFERENCES usuario(id_usuario),
  CONSTRAINT fk_usuario_rol_rol     FOREIGN KEY (id_rol)     REFERENCES rol(id_rol)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE propietario (
  id_propietario     INT         NOT NULL AUTO_INCREMENT,
  id_usuario         INT         NOT NULL,
  documento_identidad VARCHAR(20) NOT NULL,
  estado_validacion  VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
  PRIMARY KEY (id_propietario),
  CONSTRAINT uq_propietario_usuario  UNIQUE (id_usuario),
  CONSTRAINT uq_propietario_documento UNIQUE (documento_identidad),
  CONSTRAINT fk_propietario_usuario FOREIGN KEY (id_usuario) REFERENCES usuario(id_usuario),
  CONSTRAINT ck_propietario_estado CHECK (estado_validacion IN ('PENDIENTE','VALIDADO','RECHAZADO'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -------------------- Catálogo y establecimientos ---------------------------
CREATE TABLE establecimiento (
  id_establecimiento INT          NOT NULL AUTO_INCREMENT,
  id_propietario     INT          NOT NULL,
  id_distrito        INT          NOT NULL,
  nombre             VARCHAR(120) NOT NULL,
  direccion          VARCHAR(200) NOT NULL,
  referencia         VARCHAR(200) NULL,
  estado             VARCHAR(20)  NOT NULL DEFAULT 'ACTIVO',
  PRIMARY KEY (id_establecimiento),
  CONSTRAINT fk_establecimiento_propietario FOREIGN KEY (id_propietario) REFERENCES propietario(id_propietario),
  CONSTRAINT fk_establecimiento_distrito    FOREIGN KEY (id_distrito)    REFERENCES distrito(id_distrito),
  CONSTRAINT ck_establecimiento_estado CHECK (estado IN ('ACTIVO','INACTIVO'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE deporte (
  id_deporte INT         NOT NULL AUTO_INCREMENT,
  nombre     VARCHAR(60) NOT NULL,
  PRIMARY KEY (id_deporte),
  CONSTRAINT uq_deporte_nombre UNIQUE (nombre)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE caracteristica (
  id_caracteristica INT         NOT NULL AUTO_INCREMENT,
  nombre            VARCHAR(80) NOT NULL,
  PRIMARY KEY (id_caracteristica),
  CONSTRAINT uq_caracteristica_nombre UNIQUE (nombre)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE cancha (
  id_cancha          INT           NOT NULL AUTO_INCREMENT,
  id_establecimiento INT           NOT NULL,
  nombre             VARCHAR(100)  NOT NULL,
  descripcion        VARCHAR(500)  NULL,
  capacidad          INT           NOT NULL,
  precio_base        DECIMAL(10,2) NOT NULL,
  estado             VARCHAR(20)   NOT NULL DEFAULT 'ACTIVA',
  PRIMARY KEY (id_cancha),
  CONSTRAINT uq_cancha_establecimiento_nombre UNIQUE (id_establecimiento, nombre),
  CONSTRAINT fk_cancha_establecimiento FOREIGN KEY (id_establecimiento) REFERENCES establecimiento(id_establecimiento),
  CONSTRAINT ck_cancha_capacidad CHECK (capacidad > 0),
  CONSTRAINT ck_cancha_precio    CHECK (precio_base >= 0),
  CONSTRAINT ck_cancha_estado    CHECK (estado IN ('ACTIVA','INACTIVA','MANTENIMIENTO'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE cancha_deporte (
  id_cancha INT NOT NULL,
  id_deporte INT NOT NULL,
  PRIMARY KEY (id_cancha, id_deporte),
  CONSTRAINT fk_cancha_deporte_cancha  FOREIGN KEY (id_cancha)  REFERENCES cancha(id_cancha),
  CONSTRAINT fk_cancha_deporte_deporte FOREIGN KEY (id_deporte) REFERENCES deporte(id_deporte)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE cancha_caracteristica (
  id_cancha         INT          NOT NULL,
  id_caracteristica INT          NOT NULL,
  valor             VARCHAR(120) NULL,
  PRIMARY KEY (id_cancha, id_caracteristica),
  CONSTRAINT fk_cancha_caracteristica_cancha  FOREIGN KEY (id_cancha)         REFERENCES cancha(id_cancha),
  CONSTRAINT fk_cancha_caracteristica_caract  FOREIGN KEY (id_caracteristica) REFERENCES caracteristica(id_caracteristica)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE foto_cancha (
  id_foto   BIGINT       NOT NULL AUTO_INCREMENT,
  id_cancha INT          NOT NULL,
  url       VARCHAR(500) NOT NULL,
  orden     TINYINT      NOT NULL DEFAULT 1,
  principal TINYINT(1)   NOT NULL DEFAULT 0,
  PRIMARY KEY (id_foto),
  CONSTRAINT uq_foto_cancha_orden UNIQUE (id_cancha, orden),
  CONSTRAINT fk_foto_cancha_cancha FOREIGN KEY (id_cancha) REFERENCES cancha(id_cancha)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE horario_cancha (
  id_horario  INT           NOT NULL AUTO_INCREMENT,
  id_cancha   INT           NOT NULL,
  dia_semana  TINYINT       NOT NULL,
  hora_inicio TIME          NOT NULL,
  hora_fin    TIME          NOT NULL,
  precio      DECIMAL(10,2) NULL,
  activo      TINYINT(1)    NOT NULL DEFAULT 1,
  PRIMARY KEY (id_horario),
  CONSTRAINT fk_horario_cancha_cancha FOREIGN KEY (id_cancha) REFERENCES cancha(id_cancha),
  CONSTRAINT ck_horario_dia     CHECK (dia_semana BETWEEN 1 AND 7),
  CONSTRAINT ck_horario_rango   CHECK (hora_inicio < hora_fin),
  CONSTRAINT ck_horario_precio  CHECK (precio IS NULL OR precio >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE bloqueo_cancha (
  id_bloqueo BIGINT       NOT NULL AUTO_INCREMENT,
  id_cancha  INT          NOT NULL,
  inicio     DATETIME(6)  NOT NULL,
  fin        DATETIME(6)  NOT NULL,
  motivo     VARCHAR(200) NOT NULL,
  PRIMARY KEY (id_bloqueo),
  CONSTRAINT fk_bloqueo_cancha_cancha FOREIGN KEY (id_cancha) REFERENCES cancha(id_cancha),
  CONSTRAINT ck_bloqueo_rango CHECK (inicio < fin)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE promocion (
  id_promocion   INT           NOT NULL AUTO_INCREMENT,
  codigo         VARCHAR(30)   NOT NULL,
  descripcion    VARCHAR(200)  NOT NULL,
  tipo_descuento VARCHAR(15)   NOT NULL,
  valor          DECIMAL(10,2) NOT NULL,
  fecha_inicio   DATE          NOT NULL,
  fecha_fin      DATE          NOT NULL,
  estado         VARCHAR(20)   NOT NULL DEFAULT 'ACTIVA',
  PRIMARY KEY (id_promocion),
  CONSTRAINT uq_promocion_codigo UNIQUE (codigo),
  CONSTRAINT ck_promocion_tipo   CHECK (tipo_descuento IN ('PORCENTAJE','MONTO')),
  CONSTRAINT ck_promocion_valor  CHECK (valor > 0),
  CONSTRAINT ck_promocion_fechas CHECK (fecha_inicio <= fecha_fin),
  CONSTRAINT ck_promocion_estado CHECK (estado IN ('ACTIVA','INACTIVA'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE cancha_promocion (
  id_cancha    INT NOT NULL,
  id_promocion INT NOT NULL,
  PRIMARY KEY (id_cancha, id_promocion),
  CONSTRAINT fk_cancha_promocion_cancha    FOREIGN KEY (id_cancha)    REFERENCES cancha(id_cancha),
  CONSTRAINT fk_cancha_promocion_promocion FOREIGN KEY (id_promocion) REFERENCES promocion(id_promocion)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- --------------------------- Reservas ---------------------------------------
CREATE TABLE reserva (
  id_reserva     BIGINT        NOT NULL AUTO_INCREMENT,
  id_usuario     INT           NOT NULL,
  id_cancha      INT           NOT NULL,
  id_promocion   INT           NULL,
  inicio         DATETIME(6)   NOT NULL,
  fin            DATETIME(6)   NOT NULL,
  monto_cancha   DECIMAL(10,2) NOT NULL,
  monto_descuento DECIMAL(10,2) NOT NULL DEFAULT 0,
  estado         VARCHAR(20)   NOT NULL DEFAULT 'PENDIENTE',
  fecha_creacion DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id_reserva),
  CONSTRAINT fk_reserva_usuario   FOREIGN KEY (id_usuario)   REFERENCES usuario(id_usuario),
  CONSTRAINT fk_reserva_cancha    FOREIGN KEY (id_cancha)    REFERENCES cancha(id_cancha),
  CONSTRAINT fk_reserva_promocion FOREIGN KEY (id_promocion) REFERENCES promocion(id_promocion),
  CONSTRAINT ck_reserva_rango  CHECK (inicio < fin),
  CONSTRAINT ck_reserva_montos CHECK (monto_cancha >= 0 AND monto_descuento >= 0 AND monto_descuento <= monto_cancha),
  CONSTRAINT ck_reserva_estado CHECK (estado IN ('PENDIENTE','CONFIRMADA','CANCELADA','FINALIZADA'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE historial_reserva (
  id_historial    BIGINT       NOT NULL AUTO_INCREMENT,
  id_reserva      BIGINT       NOT NULL,
  estado_anterior VARCHAR(20)  NULL,
  estado_nuevo    VARCHAR(20)  NOT NULL,
  fecha_cambio    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  observacion     VARCHAR(250) NULL,
  PRIMARY KEY (id_historial),
  CONSTRAINT fk_historial_reserva FOREIGN KEY (id_reserva) REFERENCES reserva(id_reserva)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE cochera (
  id_cochera         INT           NOT NULL AUTO_INCREMENT,
  id_establecimiento INT           NOT NULL,
  nombre             VARCHAR(100)  NOT NULL,
  capacidad          INT           NOT NULL,
  tarifa_hora        DECIMAL(10,2) NOT NULL,
  estado             VARCHAR(20)   NOT NULL DEFAULT 'ACTIVA',
  PRIMARY KEY (id_cochera),
  CONSTRAINT fk_cochera_establecimiento FOREIGN KEY (id_establecimiento) REFERENCES establecimiento(id_establecimiento),
  CONSTRAINT ck_cochera_capacidad CHECK (capacidad > 0),
  CONSTRAINT ck_cochera_tarifa    CHECK (tarifa_hora >= 0),
  CONSTRAINT ck_cochera_estado    CHECK (estado IN ('ACTIVA','INACTIVA'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE reserva_cochera (
  id_reserva_cochera BIGINT        NOT NULL AUTO_INCREMENT,
  id_reserva         BIGINT        NOT NULL,
  id_cochera         INT           NOT NULL,
  inicio             DATETIME(6)   NOT NULL,
  fin                DATETIME(6)   NOT NULL,
  monto              DECIMAL(10,2) NOT NULL,
  PRIMARY KEY (id_reserva_cochera),
  CONSTRAINT uq_reserva_cochera_reserva UNIQUE (id_reserva),
  CONSTRAINT fk_reserva_cochera_reserva FOREIGN KEY (id_reserva) REFERENCES reserva(id_reserva),
  CONSTRAINT fk_reserva_cochera_cochera FOREIGN KEY (id_cochera) REFERENCES cochera(id_cochera),
  CONSTRAINT ck_reserva_cochera_rango CHECK (inicio < fin),
  CONSTRAINT ck_reserva_cochera_monto CHECK (monto >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- --------------------------- Finanzas ---------------------------------------
CREATE TABLE pago (
  id_pago          BIGINT        NOT NULL AUTO_INCREMENT,
  id_reserva       BIGINT        NOT NULL,
  monto            DECIMAL(10,2) NOT NULL,
  medio_pago       VARCHAR(30)   NOT NULL,
  codigo_operacion VARCHAR(80)   NULL,
  estado           VARCHAR(20)   NOT NULL,
  fecha_pago       DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id_pago),
  CONSTRAINT fk_pago_reserva FOREIGN KEY (id_reserva) REFERENCES reserva(id_reserva),
  CONSTRAINT ck_pago_monto  CHECK (monto > 0),
  CONSTRAINT ck_pago_estado CHECK (estado IN ('PENDIENTE','APROBADO','RECHAZADO','REEMBOLSADO'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE reembolso (
  id_reembolso      BIGINT        NOT NULL AUTO_INCREMENT,
  id_pago           BIGINT        NOT NULL,
  monto             DECIMAL(10,2) NOT NULL,
  motivo            VARCHAR(250)  NOT NULL,
  estado            VARCHAR(20)   NOT NULL DEFAULT 'SOLICITADO',
  fecha_solicitud   DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  fecha_resolucion  DATETIME(6)   NULL,
  PRIMARY KEY (id_reembolso),
  CONSTRAINT fk_reembolso_pago FOREIGN KEY (id_pago) REFERENCES pago(id_pago),
  CONSTRAINT ck_reembolso_monto  CHECK (monto > 0),
  CONSTRAINT ck_reembolso_estado CHECK (estado IN ('SOLICITADO','APROBADO','RECHAZADO','PROCESADO'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE liquidacion (
  id_liquidacion  BIGINT        NOT NULL AUTO_INCREMENT,
  id_propietario  INT           NOT NULL,
  periodo_inicio  DATE          NOT NULL,
  periodo_fin     DATE          NOT NULL,
  monto_bruto     DECIMAL(12,2) NOT NULL,
  monto_comision  DECIMAL(12,2) NOT NULL,
  monto_neto      DECIMAL(12,2) NOT NULL,
  estado          VARCHAR(20)   NOT NULL DEFAULT 'PENDIENTE',
  PRIMARY KEY (id_liquidacion),
  CONSTRAINT fk_liquidacion_propietario FOREIGN KEY (id_propietario) REFERENCES propietario(id_propietario),
  CONSTRAINT ck_liquidacion_periodo CHECK (periodo_inicio <= periodo_fin),
  CONSTRAINT ck_liquidacion_montos  CHECK (monto_bruto >= 0 AND monto_comision >= 0 AND monto_neto >= 0),
  CONSTRAINT ck_liquidacion_estado  CHECK (estado IN ('PENDIENTE','PROCESADA','PAGADA'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE detalle_liquidacion (
  id_liquidacion      BIGINT        NOT NULL,
  id_reserva          BIGINT        NOT NULL,
  monto_reserva       DECIMAL(10,2) NOT NULL,
  porcentaje_comision DECIMAL(5,2)  NOT NULL,
  monto_comision      DECIMAL(10,2) NOT NULL,
  monto_neto          DECIMAL(10,2) NOT NULL,
  PRIMARY KEY (id_liquidacion, id_reserva),
  CONSTRAINT uq_detalle_liq_reserva UNIQUE (id_reserva),
  CONSTRAINT fk_detalle_liquidacion_liquidacion FOREIGN KEY (id_liquidacion) REFERENCES liquidacion(id_liquidacion),
  CONSTRAINT fk_detalle_liquidacion_reserva     FOREIGN KEY (id_reserva)     REFERENCES reserva(id_reserva),
  CONSTRAINT ck_detalle_liquidacion_porcentaje CHECK (porcentaje_comision BETWEEN 0 AND 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------- Experiencia y soporte ------------------------------
CREATE TABLE calificacion (
  id_calificacion BIGINT       NOT NULL AUTO_INCREMENT,
  id_reserva      BIGINT       NOT NULL,
  puntaje         TINYINT      NOT NULL,
  comentario      VARCHAR(500) NULL,
  fecha_registro  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id_calificacion),
  CONSTRAINT uq_calificacion_reserva UNIQUE (id_reserva),
  CONSTRAINT fk_calificacion_reserva FOREIGN KEY (id_reserva) REFERENCES reserva(id_reserva),
  CONSTRAINT ck_calificacion_puntaje CHECK (puntaje BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE notificacion (
  id_notificacion BIGINT       NOT NULL AUTO_INCREMENT,
  id_usuario      INT          NOT NULL,
  titulo          VARCHAR(120) NOT NULL,
  mensaje         VARCHAR(500) NOT NULL,
  leida           TINYINT(1)   NOT NULL DEFAULT 0,
  fecha_envio     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id_notificacion),
  CONSTRAINT fk_notificacion_usuario FOREIGN KEY (id_usuario) REFERENCES usuario(id_usuario)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE reclamo (
  id_reclamo     BIGINT       NOT NULL AUTO_INCREMENT,
  id_usuario     INT          NOT NULL,
  id_reserva     BIGINT       NULL,
  asunto         VARCHAR(150) NOT NULL,
  descripcion    VARCHAR(800) NOT NULL,
  estado         VARCHAR(20)  NOT NULL DEFAULT 'ABIERTO',
  fecha_registro DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id_reclamo),
  CONSTRAINT fk_reclamo_usuario FOREIGN KEY (id_usuario) REFERENCES usuario(id_usuario),
  CONSTRAINT fk_reclamo_reserva FOREIGN KEY (id_reserva) REFERENCES reserva(id_reserva),
  CONSTRAINT ck_reclamo_estado CHECK (estado IN ('ABIERTO','EN_REVISION','RESUELTO','CERRADO'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE auditoria (
  id_auditoria BIGINT       NOT NULL AUTO_INCREMENT,
  id_usuario   INT          NULL,
  accion       VARCHAR(120) NOT NULL,
  entidad      VARCHAR(80)  NOT NULL,
  id_entidad   VARCHAR(60)  NULL,
  detalle      VARCHAR(800) NULL,
  fecha_evento DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id_auditoria),
  CONSTRAINT fk_auditoria_usuario FOREIGN KEY (id_usuario) REFERENCES usuario(id_usuario)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ------------------------------ Índices -------------------------------------
CREATE INDEX ix_reserva_cancha_inicio   ON reserva (id_cancha, inicio, fin);
CREATE INDEX ix_reserva_usuario_fecha   ON reserva (id_usuario, fecha_creacion);
CREATE INDEX ix_cancha_establecimiento  ON cancha (id_establecimiento, estado);
CREATE INDEX ix_horario_cancha_dia      ON horario_cancha (id_cancha, dia_semana, activo);
CREATE INDEX ix_pago_reserva_estado     ON pago (id_reserva, estado);
CREATE INDEX ix_reclamo_usuario_estado  ON reclamo (id_usuario, estado);
