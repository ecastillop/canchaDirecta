-- Configuracion persistente de la plataforma (Bloque C del delta).
-- Las claves son estables: el backend las lee por nombre y la API expone un objeto tipado.
CREATE TABLE configuracion_plataforma (
  clave              VARCHAR(60)  NOT NULL,
  valor              VARCHAR(200) NOT NULL,
  descripcion        VARCHAR(255) NULL,
  fecha_actualizacion DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (clave)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO configuracion_plataforma (clave, valor, descripcion) VALUES
  ('comision_porcentaje', '10.00',
   'Comision de plataforma (%) descontada al liquidar las reservas pagadas.'),
  ('horas_cancelacion_gratis', '24',
   'Horas de antelacion minima para que la cancelacion del cliente genere reembolso total.'),
  ('horas_minimas_cancelacion', '2',
   'Horas de antelacion minima para que el cliente pueda cancelar su reserva.'),
  ('dias_anticipacion_max', '365',
   'Dias maximos de anticipacion con los que se puede reservar o consultar disponibilidad.'),
  ('correo_soporte', 'soporte@canchadirecta.pe',
   'Correo de contacto publicado para consultas de soporte.');
