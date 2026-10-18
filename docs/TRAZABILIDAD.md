# Matriz de trazabilidad

`FUENTE -> RF -> CASO_DE_USO -> ENDPOINT -> PANTALLA -> PRUEBA -> ESTADO`

Estados: `IMPLEMENTADO` - `PARCIAL` - `PENDIENTE` - `BLOCKED` - `NOT RUN`.
Actualizar a medida que avance la implementacion. Ningun `PASS` sin ejecucion real
(ver `RESULTADOS_PRUEBAS.md`).

## A. Historias originales (S1)

| ID | Fuente | Descripcion | Modulo | API | Pantalla | Prueba | Estado |
|---|---|---|---|---|---|---|---|
| HU-001 | S1 | Registro de cuenta | identity | POST /api/v1/auth/register | Registro | RegistroUsuarioTest (4) | **IMPLEMENTADO** |
| HU-002 | S1 | Inicio de sesion | identity | POST /api/v1/auth/login, /refresh, /logout | Login | AutenticacionTest (5) + SesionRefreshTest (5) | **IMPLEMENTADO** |
| HU-003 | S1 | Registro/actualizacion de sedes | venues | `POST /api/v1/establecimientos`, `PUT/DELETE /api/v1/establecimientos/{id}` (con ownership) | Establecimientos | `EdicionYBajaVenuesTest` + smoke | **IMPLEMENTADO** |
| HU-004 | S1 | Cancha (deporte/capacidad/precio/descripcion) | venues | `POST /api/v1/establecimientos/{id}/canchas`, `PUT/DELETE /api/v1/canchas/{id}`, atributos y fotos | Canchas | `CatalogoCanchaTest`, `EdicionYBajaVenuesTest` + smoke | **IMPLEMENTADO** |
| HU-005 | S1 | Horarios semanales y validaciones | venues | GET/POST /api/v1/canchas/{id}/horarios, DELETE .../horarios/{id} | Horarios | AgendaTest (4 tests) + smoke | **IMPLEMENTADO** (franjas por dia con validacion de solapes; la disponibilidad usa la agenda real) |
| HU-006 | S1 | Busqueda, filtros, ficha, disponibilidad | catalog | `GET /api/v1/canchas` (distrito, deporte, precio maximo, texto), ficha, `GET /api/v1/canchas/comparar`, `GET /api/v1/canchas/{id}/disponibilidad` | Catalogo/Detalle | `CatalogoCanchaTest`, `EdicionYBajaVenuesTest`, disponibilidad + smoke | **IMPLEMENTADO** |
| HU-007 | S1 | Seleccion fecha/hora + reserva concurrente | bookings | POST /api/v1/reservas | Reserva | ReservasTest (14 tests, 30 hilos) | **IMPLEMENTADO** |
| HU-008-1 | S1 | Historial de reservas del usuario | bookings | GET /api/v1/reservas/mias | Mis reservas | ReservasTest + smoke | **IMPLEMENTADO** |
| HU-008-2 | S1 | Reservas de recursos del propietario | bookings | GET /api/v1/reservas/recibidas + POST /api/v1/reservas/{id}/confirmar + POST /api/v1/reservas/{id}/rechazar | Bandeja y decisiones del propietario | ReservasTest (E-14) + smoke (E-15) | **IMPLEMENTADO** |

## B. Casos de uso del ZIP (S3)

| ID | Fuente | Caso de uso | Modulo | Estado |
|---|---|---|---|---|
| CU01.1 | CU_01 | Registrar cuenta | identity | **IMPLEMENTADO** |
| CU01.2 | CU_01 | Verificar correo | identity | **IMPLEMENTADO** (columna `usuario.correo_verificado`, token `EMAIL_VERIFY`, `POST /auth/email/verify`, reenvio y flag `cd.auth.requerir-correo-verificado`) |
| CU01.3 | CU_01 | Iniciar sesion | identity | **IMPLEMENTADO** |
| CU01.4 | CU_01 | Renovar sesion (refresh con rotacion) | identity | **IMPLEMENTADO** |
| CU01.5 | CU_01 | Cerrar sesion | identity | **IMPLEMENTADO** |
| CU01.6 | CU_01 | Recuperar contrasena | identity | **IMPLEMENTADO** |
| CU01.4 | CU_01 | Recuperar contrasena | identity | **IMPLEMENTADO** (`RecuperacionPasswordTest`, 5 tests) |
| CU01.5 | CU_01 | Perfil editable | identity | **IMPLEMENTADO** (`GET/PUT /users/me`) |
| CU01.6 | CU_01 | Solicitar alta de propietario | identity/platform | **IMPLEMENTADO** (`POST /propietarios/solicitudes`, `GET /propietarios/me`; validacion admin CU06.2) |
| CU01.7 | CU_01 | Validar propietario (admin) | platform | **IMPLEMENTADO** |
| CU02.1 | CU_02 | Buscar/filtrar canchas | catalog | **IMPLEMENTADO** (distrito, deporte, precio maximo y texto) |
| CU02.2 | CU_02 | Comparar canchas | catalog | **IMPLEMENTADO** (`GET /canchas/comparar?ids=`, 2 a 4 fichas) |
| CU02.3 | CU_02 | Detalle de cancha | catalog | **IMPLEMENTADO** (ficha con atributos, fotos y reputacion) |
| CU02.4 | CU_02 | Calcular disponibilidad | bookings | **IMPLEMENTADO** |
| CU02.5 | CU_02 | Ver calificaciones | engagement | **IMPLEMENTADO** |
| CU03.1 | CU_03 | Seleccionar fecha/horario | bookings | **IMPLEMENTADO** |
| CU03.2 | CU_03 | Crear reserva (validada, idempotente) | bookings | **IMPLEMENTADO** (locking + `Idempotency-Key`; `UNIQUE(id_usuario, clave_idempotencia)`) |
| CU03.3 | CU_03 | Anadir cochera opcional | venues/bookings | **IMPLEMENTADO** |
| CU03.4 | CU_03 | Pago simulado | payments | **IMPLEMENTADO** |
| CU03.5 | CU_03 | Historial de reservas | bookings | **IMPLEMENTADO** |
| CU03.6 | CU_03 | Cancelar reserva | bookings | **IMPLEMENTADO** |
| CU03.7 | CU_03 | Solicitar modificacion | bookings | **IMPLEMENTADO** (`POST /reservas/{id}/modificar`: reprograma reserva vigente propia conservando duracion e importes) |
| CU03.8 | CU_03 | Calificar reserva | engagement | **IMPLEMENTADO** |
| CU04.1 | CU_04 | Gestion de establecimientos | venues | **IMPLEMENTADO** (alta, edicion y baja logica con ownership) |
| CU04.2 | CU_04 | Gestion de canchas/fotos/caracteristicas | venues | **IMPLEMENTADO** (alta, edicion, baja logica, deportes, caracteristicas y fotos) |
| CU04.3 | CU_04 | Horarios y precios por franja | venues | **IMPLEMENTADO** |
| CU04.4 | CU_04 | Bloqueos/mantenimiento | venues | **IMPLEMENTADO** |
| CU04.5 | CU_04 | Cocheras y cupos | venues | **IMPLEMENTADO** |
| CU04.6 | CU_04 | Promociones | venues | **IMPLEMENTADO** (alta por propietario y aplicacion al precio) |
| CU04.7 | CU_04 | Reservas recibidas y estados | bookings | **IMPLEMENTADO** |
| CU04.8 | CU_04 | Ingresos y liquidaciones | settlements | **IMPLEMENTADO** |
| CU05.1 | CU_05 | Procesar/registrar pago | payments | **IMPLEMENTADO** |
| CU05.2 | CU_05 | Calcular comision | settlements | **IMPLEMENTADO** |
| CU05.3 | CU_05 | Solicitar reembolso | payments | **IMPLEMENTADO** |
| CU05.4 | CU_05 | Generar/consultar liquidacion | settlements | **IMPLEMENTADO** |
| CU06.1 | CU_06 | Gestionar usuarios y roles | platform | **IMPLEMENTADO** (listado/filtros, roles y estado de cuenta) |
| CU06.2 | CU_06 | Validar propietarios | platform | **IMPLEMENTADO** |
| CU06.3 | CU_06 | Supervisar sedes/canchas | platform | **IMPLEMENTADO** (listado de solo lectura) |
| CU06.4 | CU_06 | Supervisar reservas/pagos/reembolsos | platform | **IMPLEMENTADO** (listado de solo lectura) |
| CU06.5 | CU_06 | Cocheras/promociones | platform | **IMPLEMENTADO** (catalogo de promociones; supervision y estado de cocheras) |
| CU06.6 | CU_06 | Reclamos | engagement | **IMPLEMENTADO** (usuario + moderacion `PLATFORM_ADMIN`) |
| CU06.7 | CU_06 | Auditoria | platform | **IMPLEMENTADO** (acciones administrativas) |

## C. Modelo de datos (28 tablas fuente + extensiones)

| Tabla | Migracion | Estado |
|---|---|---|
| distrito (nombre -> provincia) | V1 (28t) + V2 (jerarquia) | IMPLEMENTADO |
| usuario | V1 (28t) | IMPLEMENTADO |
| rol | V1 (28t) | IMPLEMENTADO |
| usuario_rol | V1 (28t) | IMPLEMENTADO |
| propietario | V1 (28t) | IMPLEMENTADO |
| establecimiento | V1 (28t) | IMPLEMENTADO |
| deporte | V1 (28t) | IMPLEMENTADO |
| caracteristica | V1 (28t) | IMPLEMENTADO |
| cancha | V1 (28t) | IMPLEMENTADO |
| cancha_deporte | V1 (28t) | IMPLEMENTADO |
| cancha_caracteristica | V1 (28t) | IMPLEMENTADO |
| foto_cancha | V1 + V3 (unico principal) | IMPLEMENTADO |
| horario_cancha | V1 (28t) | IMPLEMENTADO |
| bloqueo_cancha | V1 (28t) | IMPLEMENTADO |
| promocion | V1 (28t) | IMPLEMENTADO |
| cancha_promocion | V1 (28t) | IMPLEMENTADO |
| reserva | V1 (28t) | IMPLEMENTADO |
| historial_reserva | V1 (28t) | IMPLEMENTADO |
| cochera | V1 (28t) | IMPLEMENTADO |
| reserva_cochera | V1 (28t) | IMPLEMENTADO |
| pago | V1 + V3 (unico aprobado) | IMPLEMENTADO |
| reembolso | V1 (28t) | IMPLEMENTADO |
| liquidacion | V1 (28t) | IMPLEMENTADO |
| detalle_liquidacion | V1 (28t) | IMPLEMENTADO |
| calificacion | V1 (28t) | IMPLEMENTADO |
| notificacion | V1 (28t) | IMPLEMENTADO |
| reclamo | V1 (28t) | IMPLEMENTADO |
| auditoria | V1 (28t) | IMPLEMENTADO |
| departamento | V2 | IMPLEMENTADO |
| provincia | V2 | IMPLEMENTADO |
| auth_token | V3 | IMPLEMENTADO |
| sesion_refresh | V3 | IMPLEMENTADO |
| idempotencia | V3 | IMPLEMENTADO |
| solicitud_cambio_reserva | V3 | IMPLEMENTADO |
| config_establecimiento | V3 | IMPLEMENTADO |
| reclamo_seguimiento | V3 | IMPLEMENTADO |
| pago_evento | V3 | IMPLEMENTADO |

## D. Reglas de negocio (RN) y restricciones

| ID | Regla | Implementacion | Prueba | Estado |
|---|---|---|---|---|
| RN-01 | Sin reservas activas solapadas | `SELECT ... FOR UPDATE` sobre la fila de `cancha` + comprobacion de solape bajo el mismo bloqueo (ADR-0005) | IT-concurrency: 30 hilos simultaneos -> 1 reserva (E-08) | **IMPLEMENTADO / RUN** |
| RN-02 | fin > inicio | CHECK en reserva/bloqueo/horario/cochera (V1) + `FranjaHoraria` (30-180 min) | API 400 con `fin = inicio` (E-08) | **IMPLEMENTADO / RUN** |
| RN-03 | No reservar en bloqueos | `existeBloqueoSolapado` dentro del caso de uso y bajo el mismo bloqueo; alta de bloqueos por el propietario bajo el mismo `FOR UPDATE` (ADR-0017) | `AgendaTest`: franja bloqueada no se ofrece (200), reserva 409 `SLOT_UNAVAILABLE`, bloqueo sobre reserva vigente 409 (E-12) | **IMPLEMENTADO / RUN** |
| RN-04 | Una calificacion por reserva | UNIQUE(id_reserva) V1 + `409` comprobado antes de insertar | `CalificacionTest` | **IMPLEMENTADO** / RUN (falta carrera concurrente) |
| RN-05 | Max. un pago aprobado/reserva | columna generada + UNIQUE V3 (ADR-0006) + comprobacion `409` en `PagoSimuladoService` (ADR-0020) | `PagoTest`: segundo pago `409 CONFLICT` y un solo `APROBADO` en BD (E-18) | **IMPLEMENTADO / RUN** |
| RN-06 | Max. una foto principal/cancha | columna generada + UNIQUE V3 | IT-photo | IMPLEMENTADO (esquema) / NOT RUN |
| RN-07 | Reserva en max. una liquidacion | UNIQUE(id_reserva) en detalle_liquidacion V1 + `NOT EXISTS` sobre `detalle_liquidacion` y lock del propietario al generar (`LiquidacionService`, ADR-0021) | `LiquidacionTest`: la misma reserva no se liquida dos veces (E-20) | **IMPLEMENTADO / RUN** |
| RN-08 | Cochera opcional segun capacidad | cochera de un establecimiento (`POST /api/v1/establecimientos/{id}/cocheras`), reserva con `cocheraId` opcional; lock de la fila de la cochera en orden consistente cancha -> cochera y cupo por solape de `reserva_cochera` vigente (ADR-0019) | `CocheraTest`: capacidad 1/2, intervalos parciales, coherencia de establecimiento, liberacion al cancelar y 4 canchas concurrentes -> exactamente 2 reservas (E-16) | **IMPLEMENTADO / RUN** |

## E. Areas funcionales del prompt (F01-F07)

| Area | Endpoints | Estado |
|---|---|---|
| F01 Identidad y acceso | auth/*, me | **IMPLEMENTADO** (registro, verificacion de correo, login con limite de intentos, refresh, logout, recuperacion de contrasena, perfil editable y solicitud de propietario) |
| F02 Geografia/deportes/catalogo | geography/*, sports, amenities, courts | **IMPLEMENTADO** (`GET /api/v1/canchas` con filtros por distrito, deporte, precio maximo y texto; `GET /api/v1/ubicaciones/distritos` con provincia/departamento; catalogos; ficha y comparacion de canchas) |
| F03 Propietario/sedes/canchas | owner/venues, owner/courts, schedules, blocks, parking, promotions | **IMPLEMENTADO** (alta, edicion y baja logica de establecimiento/cancha; horarios y bloqueos; cocheras; promociones aplicadas; ficha con deportes, caracteristicas y fotos editables) |
| F04 Disponibilidad/reservas | bookings/*, availability | **IMPLEMENTADO** (reserva con locking y `SLOT_UNAVAILABLE`, `Idempotency-Key`, cochera opcional con cupo, listado propio, reprogramacion con duracion fija, disponibilidad por fecha segun agenda, cancelacion con ventana minima, atencion por el propietario, revocacion de una reserva confirmada, expiracion del hold y cierre automatico) |
| F05 Pagos/liquidaciones | payments/simulate, refund-requests, owner/settlements | **PARCIAL - FUERA DE ALCANCE** (pago simulado determinista y reembolso por cancelacion/rechazo; comision y liquidacion del propietario; expiracion por `hold_pago_min` y cierre de reservas concluidas; **pasarela real y desembolso real no se implementan en esta entrega por decision del usuario**, ver `L-12`) |
| F06 Soporte/avisos/calificaciones | notifications, complaints, reviews | **IMPLEMENTADO** (calificaciones, reclamos con moderacion y avisos in-app; correo/SMS real fuera de alcance, `L-17`) |
| F07 Administracion | admin/* | **IMPLEMENTADO** (CU06.1-CU06.7: usuarios/roles/estado, reclamos, propietarios, supervision, auditoria, cocheras y promociones) |

## F. Infraestructura / calidad

| Elemento | Estado | Evidencia |
|---|---|---|
| Esquema MySQL (Flyway) | IMPLEMENTADO | 6 migraciones aplicadas; 38 tablas |
| Backend compila y arranca | IMPLEMENTADO | `mvnw test` -> BUILD SUCCESS |
| Slice identidad (registro/login/JWT/me) | IMPLEMENTADO | 10 tests PASS |
| Liquidaciones del propietario (F05) | IMPLEMENTADO | `LiquidacionTest` 10 tests + humo 59/59 (E-20/E-21) |
| Expiracion del hold de pago + revocacion (L-14) | IMPLEMENTADO | `ExpiracionReservasTest` 4 + `ReservasTest` revocacion 3 + humo 63/63 (E-22/E-23) |
| Cierre automatico de reservas concluidas (L-15) | IMPLEMENTADO | `FinalizarReservasService` (`@Scheduled`) + `FinalizacionReservasTest` 4 (E-24) |
| Frontend Angular | **IMPLEMENTADO / RUN** | Angular 22: catálogo paginado, detalle con calendario, reservas, perfil (reenvío de verificación), reclamos, notificaciones, panel del propietario con descubrimiento automático de establecimientos (`/establecimientos/mios`), edición/baja, canchas, agenda, promociones y liquidaciones; administración con roles en modal y listas paginadas; registro con auto-login y retorno a la ruta original. `npm run build` OK, 15/15 unitarios, E2E usuario + propietario/admin OK y a11y OK (E-43). |
| Docker/Compose | **IMPLEMENTADO Y VERIFICADO** | `compose up` (Modo C) + smoke 10/10 en :8089 |
| OpenAPI/Swagger | **IMPLEMENTADO** | extension presente; `/q/openapi` y `/q/swagger-ui` publicados con titulo/version y anotaciones por endpoint |
| CI | **IMPLEMENTADO** | `.github/workflows/backend.yml` (MySQL 8.4 efimero, JDK 21, clave JWT, `mvnw test`) |
