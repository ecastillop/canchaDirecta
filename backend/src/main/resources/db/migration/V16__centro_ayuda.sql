-- Centro de ayuda: categorias y preguntas frecuentes publicas.
CREATE TABLE ayuda_categoria (
  id_categoria INT         NOT NULL AUTO_INCREMENT,
  nombre       VARCHAR(80) NOT NULL,
  slug         VARCHAR(80) NOT NULL,
  orden        INT         NOT NULL DEFAULT 0,
  PRIMARY KEY (id_categoria),
  CONSTRAINT uq_ayuda_categoria_slug UNIQUE (slug)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ayuda_articulo (
  id_articulo    INT          NOT NULL AUTO_INCREMENT,
  id_categoria   INT          NOT NULL,
  pregunta       VARCHAR(200) NOT NULL,
  respuesta      TEXT         NOT NULL,
  orden          INT          NOT NULL DEFAULT 0,
  estado         VARCHAR(20)  NOT NULL DEFAULT 'ACTIVO',
  fecha_registro DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id_articulo),
  CONSTRAINT fk_ayuda_articulo_categoria FOREIGN KEY (id_categoria)
    REFERENCES ayuda_categoria(id_categoria),
  CONSTRAINT ck_ayuda_articulo_estado CHECK (estado IN ('ACTIVO','INACTIVO'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_ayuda_articulo_categoria ON ayuda_articulo (id_categoria, orden);

INSERT INTO ayuda_categoria (id_categoria, nombre, slug, orden) VALUES
  (1, 'Reservas', 'reservas', 1),
  (2, 'Pagos y reembolsos', 'pagos', 2),
  (3, 'Mi cuenta', 'cuenta', 3),
  (4, 'Actividades', 'actividades', 4),
  (5, 'Cupones y promociones', 'cupones', 5);

INSERT INTO ayuda_articulo (id_categoria, pregunta, respuesta, orden) VALUES
  (1, '¿Cómo reservo una cancha?',
   'Busca un espacio en la sección Canchas, elige la cancha y la fecha, selecciona una franja disponible y confirma la reserva. Si es tu primera vez, deberás iniciar sesión o crear una cuenta.', 1),
  (1, '¿Puedo cancelar o reprogramar mi reserva?',
   'Sí. Desde Mis reservas puedes cancelar mientras la reserva esté pendiente o confirmada y la franja lo permita. La reprogramación está disponible para reservas propias dentro del plazo permitido.', 2),
  (1, '¿Qué pasa si el propietario rechaza mi reserva?',
   'La reserva cambia a RECHAZADA, la franja se libera y, si ya habías pagado, se registra el reembolso simulado. Puedes reservar otra franja o cancha.', 3),
  (2, '¿Cómo funciona el pago?',
   'El pago es simulado: eliges un medio de pago de demostración y la plataforma registra el cobro. No se realiza ningún cargo real.', 1),
  (2, '¿Cuándo recibo un reembolso?',
   'Cuando una reserva pagada se cancela o es rechazada, el sistema genera un reembolso simulado por el monto correspondiente y lo muestra en el detalle de la reserva.', 2),
  (2, '¿Los montos incluyen comisión?',
   'El precio de la cancha y los extras se muestran antes de confirmar. La comisión de plataforma se calcula en las liquidaciones del propietario, no se suma al precio publicado.', 3),
  (3, '¿Cómo creo una cuenta?',
   'Usa el botón Mi cuenta y elige Crear cuenta. Necesitas nombres, apellidos, correo, teléfono y una contraseña. Después podrás verificar tu correo e iniciar sesión.', 1),
  (3, 'Olvidé mi contraseña, ¿qué hago?',
   'En la pantalla de ingreso usa Recuperar contraseña. Recibirás un enlace o código para definir una nueva clave.', 2),
  (3, '¿Cómo me convierto en propietario?',
   'Desde Mi perfil envía tu solicitud de propietario con tu documento de identidad. Un administrador la valida y, al aprobarla, podrás publicar tu establecimiento y tus canchas.', 3),
  (4, '¿Cómo me inscribo a una actividad?',
   'Ingresa a Actividades, elige el torneo, clase o partido abierto y pulsa Inscribirme. Debes tener sesión iniciada y quedarán cupos disponibles.', 1),
  (4, '¿Puedo cancelar mi inscripción?',
   'Sí, desde Mis inscripciones o desde la misma tarjeta de la actividad. El cupo vuelve a quedar disponible para otros usuarios.', 2),
  (4, '¿Qué pasa si se cancela la actividad?',
   'Todas las inscripciones pasan a estado CANCELADA y la actividad deja de mostrarse en el listado público.', 3),
  (5, '¿Dónde ingreso un cupón?',
   'Al confirmar una reserva, escribe el código en el campo de cupón o promoción. El descuento se aplica al monto de la cancha antes del pago.', 1),
  (5, '¿Por qué mi cupón no aplica?',
   'El cupón debe estar activo, vigente para la fecha de la reserva, asociado a la cancha y con usos disponibles. Si ya lo usaste o se agotaron los canjes, la plataforma mostrará un conflicto.', 2);
