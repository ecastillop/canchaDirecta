-- ============================================================================
-- V4__seed_catalogs.sql
-- Semillas de catálogo (datos no personales). Geografía mínima: Lima.
-- Trazabilidad: docs/MODELO_DATOS.md §5, F02. Datos de demostración
-- (usuarios/canchas) NO van aquí: viven en db/seed-dev y solo se aplican en dev.
-- ============================================================================

SET NAMES utf8mb4;

-- Roles del sistema
INSERT INTO rol (nombre) VALUES ('USER'), ('OWNER'), ('PLATFORM_ADMIN');

-- Deportes
INSERT INTO deporte (nombre) VALUES
  ('Fútbol'), ('Fútbol 7'), ('Fútbol Sala / Futsal'),
  ('Básquet'), ('Vóley'), ('Tenis'), ('Pádel'), ('Frontón');

-- Características
INSERT INTO caracteristica (nombre) VALUES
  ('Césped sintético'), ('Césped natural'), ('Techada'), ('Iluminación nocturna'),
  ('Vestuarios'), ('Duchas'), ('Estacionamiento'), ('Cafetería'),
  ('Graderías'), ('Alquiler de balón'), ('Arbitraje'), ('Cercado perimetral');

-- Geografía mínima (Lima). Ampliable con catálogos oficiales verificables.
INSERT INTO departamento (nombre, codigo_inei) VALUES ('Lima', '15');

INSERT INTO provincia (id_departamento, nombre, codigo_inei)
SELECT id_departamento, 'Lima', '1501' FROM departamento WHERE codigo_inei = '15';

INSERT INTO distrito (id_provincia, nombre, codigo_ubigeo)
SELECT p.id_provincia, d.nombre, d.ubigeo
FROM provincia p
JOIN (
  SELECT '150101' AS ubigeo, 'Lima'      AS nombre UNION ALL
  SELECT '150102', 'Ancón'      UNION ALL
  SELECT '150103', 'Ate'        UNION ALL
  SELECT '150104', 'Barranco'   UNION ALL
  SELECT '150105', 'Breña'      UNION ALL
  SELECT '150106', 'Carabayllo' UNION ALL
  SELECT '150108', 'Chorrillos' UNION ALL
  SELECT '150110', 'Comas'      UNION ALL
  SELECT '150113', 'La Molina'  UNION ALL
  SELECT '150116', 'La Victoria' UNION ALL
  SELECT '150122', 'Miraflores' UNION ALL
  SELECT '150131', 'San Borja'  UNION ALL
  SELECT '150132', 'San Isidro' UNION ALL
  SELECT '150135', 'San Juan de Lurigancho' UNION ALL
  SELECT '150136', 'San Juan de Miraflores' UNION ALL
  SELECT '150140', 'Santiago de Surco' UNION ALL
  SELECT '150142', 'Surquillo'  UNION ALL
  SELECT '150143', 'Villa El Salvador' UNION ALL
  SELECT '150144', 'Villa María del Triunfo'
) d ON 1 = 1
WHERE p.codigo_inei = '1501';
