-- ============================================================================
-- V2__geography_hierarchy.sql
-- Integración del primer avance (PDF/PPTX): recupera departamento y provincia
-- y relaciona distrito con provincia. Elimina la unicidad global insuficiente
-- de distrito.nombre y la reemplaza por unicidad contextual + código ubigeo.
-- Trazabilidad: docs/REQUISITOS_Y_RECONCILIACION.md §4, ADR-0003.
-- Migración "expand": no elimina datos ni columnas existentes.
-- ============================================================================

SET NAMES utf8mb4;

CREATE TABLE departamento (
  id_departamento INT         NOT NULL AUTO_INCREMENT,
  nombre          VARCHAR(80) NOT NULL,
  -- Código oficial INEI (ubigeo de 2 dígitos). NULL si no se conoce.
  codigo_inei     CHAR(2)     NULL,
  PRIMARY KEY (id_departamento),
  CONSTRAINT uq_departamento_nombre UNIQUE (nombre),
  CONSTRAINT uq_departamento_codigo UNIQUE (codigo_inei)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE provincia (
  id_provincia     INT         NOT NULL AUTO_INCREMENT,
  id_departamento  INT         NOT NULL,
  nombre           VARCHAR(80) NOT NULL,
  codigo_inei      CHAR(4)     NULL,
  PRIMARY KEY (id_provincia),
  CONSTRAINT uq_provincia_departamento_nombre UNIQUE (id_departamento, nombre),
  CONSTRAINT uq_provincia_codigo UNIQUE (codigo_inei),
  CONSTRAINT fk_provincia_departamento FOREIGN KEY (id_departamento) REFERENCES departamento(id_departamento)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- distrito: añadir FK a provincia y código ubigeo (6 dígitos)
ALTER TABLE distrito
  ADD COLUMN id_provincia INT    NULL AFTER id_distrito,
  ADD COLUMN codigo_ubigeo CHAR(6) NULL AFTER nombre;

-- Reemplazar la unicidad global por unicidad contextual + ubigeo
ALTER TABLE distrito DROP INDEX uq_distrito_nombre;
ALTER TABLE distrito
  ADD CONSTRAINT uq_distrito_provincia_nombre UNIQUE (id_provincia, nombre),
  ADD CONSTRAINT uq_distrito_ubigeo           UNIQUE (codigo_ubigeo),
  ADD CONSTRAINT fk_distrito_provincia        FOREIGN KEY (id_provincia) REFERENCES provincia(id_provincia);
