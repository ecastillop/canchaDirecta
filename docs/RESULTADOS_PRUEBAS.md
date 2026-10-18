# Resultados de pruebas

Registro real de ejecuciones. No se escribe `PASS` sin haber ejecutado.
Estados: `PASS` - `FAIL` - `BLOCKED` - `NOT RUN`.

## Entorno

- Fecha: 2026-10-16
- SO: Windows 11 (10.0.26200) - JDK 21.0.2 (`B:\cursos\java\jdk-21.0.2`) - Maven 3.9.9 (wrapper)
- MySQL: 8.0.41 en `127.0.0.1:3306`, base `canchadirecta`
- Docker: 29.4.3 (contenedores Linux) - Compose v5.1.3

## Ejecuciones

### E-01 - Bootstrap seguro de BD
- **Comando:** `powershell -File scripts/db-bootstrap-safe.ps1`
- **Resultado:** `PASS`
- **Evidencia:** base `canchadirecta` (utf8mb4/utf8mb4_0900_ai_ci); usuarios
  `canchadirecta_mig`, `canchadirecta_app` en `localhost` y `127.0.0.1`; login del
  usuario de runtime OK; ninguna otra base modificada.

### E-02 - Migraciones Flyway + arranque del backend
- **Comando:** `backend\mvnw.cmd -B test`
- **Resultado:** `PASS`
- **Evidencia:**
  - Flyway: `Successfully applied 4 migrations to schema canchadirecta, now at version v4`.
  - `flyway_schema_history`: 4 filas, `success = 1` (baseline / geography / integrity / seed).
  - `information_schema.tables` en `canchadirecta`: **38** tablas.
  - `BUILD SUCCESS`.

### E-03 - Slice identidad: registro + login JWT + /users/me
- **Comando:** `backend\mvnw.cmd -B test`
- **Resultado:** `PASS`
- **Evidencia:** `Tests run: 10, Failures: 0, Errors: 0, Skipped: 0` - `BUILD SUCCESS`.
  - `SchemaMigrationTest` (1): 28 tablas fuente + `departamento`/`provincia` + `flyway_schema_history`.
  - `RegistroUsuarioTest` (4): alta 201 con rol `USER`; correo duplicado -> 409
    `EMAIL_ALREADY_REGISTERED`; contrasena < 8 -> 400 `VALIDATION_ERROR`; confirmacion
    distinta -> 400. La respuesta nunca expone el hash.
  - `AutenticacionTest` (5): login correcto -> 200 con `accessToken`, `refreshToken`,
    `tokenType=Bearer`, `expiresIn>0`; contrasena erronea -> 401
    `INVALID_CREDENTIALS`; correo inexistente -> 401; `/users/me` sin token -> 401;
    `/users/me` con token -> 200 con el correo del usuario.
- **Nota:** los tests usan la base `canchadirecta` real con correos unicos; Docker ya esta
  operativo, por lo que pueden migrarse a Testcontainers (`L-09`).

### E-04 - Rotacion de refresh token y cierre de sesion
- **Comando:** `backend\mvnw.cmd -B test`
- **Resultado:** `PASS`
- **Evidencia:** `Tests run: 15, Failures: 0, Errors: 0, Skipped: 0` - `BUILD SUCCESS`.
  - `SesionRefreshTest` (5): refresh valido -> 200 con tokens nuevos (el refresh cambia);
    reutilizar un refresh ya rotado -> 401 `UNAUTHENTICATED`; la reutilizacion **revoca
    todas** las sesiones del usuario (el refresh rotado tambien deja de servir);
    logout -> 204 y el refresh deja de servir; refresh inexistente -> 401.
  - `AutenticacionTest` (5) y `RegistroUsuarioTest` (4) siguen en verde; `SchemaMigrationTest` (1).

### E-05 - Prueba de humo de la API (HTTP real)
- **Comando:** `scripts\smoke.ps1 -BaseUrl http://localhost:8088` (backend empaquetado
  y arrancado con el perfil `prod` contra el MySQL real)
- **Resultado:** `PASS` - `RESULTADO: OK (10/10 pasos)`
- **Evidencia:** ping; health `UP`; registro 201; registro duplicado 409; login 200;
  `/users/me` sin token 401; `/users/me` con token 200; refresh rota el token;
  reutilizacion del refresh 401; logout 204 y el refresh deja de servir.
- **Nota:** `scripts/smoke.sh` es el equivalente para Linux/Git Bash (misma secuencia,
  usando `curl` + `jq`/`python3`).

### E-06 - Stack completo en Docker (Modo C: MySQL aislado + backend)
- **Comando:** `docker compose -f compose.yml -f compose.mysql.yml up -d --build mysql backend`
- **Resultado:** `PASS` (exit=0)
- **Evidencia:**
  - Imagen `canchadirecta/backend:0.1.0` construida (multi-stage: `eclipse-temurin:21-jdk` -> `21-jre`).
  - `canchadirecta-mysql-1` (mysql:8.4) -> `Up (healthy)`, publicado en `0.0.0.0:3309->3306`.
  - `canchadirecta-backend-1` -> `Up (healthy)`, publicado en `0.0.0.0:8089->8080`;
    `/q/health/ready` -> `UP` (incluye el *health check* de conexion a base de datos).
  - Dentro del contenedor MySQL: **38 tablas** y las 4 migraciones Flyway con `success=1`.
  - Volumen persistente `canchadirecta_cd-mysql-data` creado.
  - **`smoke.ps1 -BaseUrl http://localhost:8089` -> `RESULTADO: OK (10/10 pasos)`** (registro, login,
    `/users/me` con y sin token, rotacion de refresh, reutilizacion 401, logout 204) - todo
    atravesando el stack contenedorizado.
  - La imagen **no contiene** la clave JWT: `find /app -name '*.jwk'` -> `0` (se monta de solo lectura).

### E-07 - Recuperacion de contrasena + Swagger en Docker
- **Comando:** `backend\mvnw.cmd -B test` y verificacion HTTP sobre el contenedor
- **Resultado:** `PASS`
- **Evidencia:**
  - `Tests run: 20, Failures: 0, Errors: 0, Skipped: 0` - `BUILD SUCCESS`
    (`RecuperacionPasswordTest` 5/5: cambio de clave 204 + login con la nueva 200 + login con
    la anterior 401 + las sesiones previas quedan revocadas 401; token no reutilizable 400;
    correo inexistente -> 202 sin emitir token; token invalido -> 400; confirmacion distinta -> 400).
  - **Swagger UI en el contenedor:** `/q/swagger-ui` -> **200** (antes 404: el perfil `prod`
    tenia `%prod.quarkus.swagger-ui.always-include=false`). `/q/openapi` publica las **8 rutas**.
  - **Flujo real sobre el contenedor** (`http://localhost:8089`): `password-reset/request` -> 202;
    token leido del log del notificador simulado; `password-reset/confirm` -> 204;
    login con la contrasena anterior -> **401**; login con la nueva -> **200**.
  - `smoke.ps1` ampliado a 12 pasos -> **12/12 PASS**.

### E-08 - Venues + reservas con `SELECT ... FOR UPDATE` (RN-01)

- **Comando:** `backend\mvnw.cmd -B test` (desde `canchadirecta`, con `JAVA_HOME` = JDK 21 y las
  variables de `.env` cargadas en el entorno).
- **Resultado:** **PASS** - `Tests run: 27, Failures: 0, Errors: 0, Skipped: 0` - `BUILD SUCCESS`.
- **Evidencia:**
  - `ReservasTest` **7/7 PASS**.
  - **RN-01 (nucleo):** **30 peticiones HTTP simultaneas** (hilos liberados a la vez con
    `CountDownLatch`, cliente HTTP de la JDK) a la misma franja de la misma cancha ->
    **exactamente 1 respuesta 201**, **29 respuestas 409 `SLOT_UNAVAILABLE`** y **1 sola fila
    vigente** en `reserva` (contada por SQL). Repetido 3 veces seguidas: **3/3 PASS**.
  - Solape: misma franja secuencial -> 409; franjas contiguas (19:00-20:00 y 20:00-21:00) ->
    201 + 201 (los intervalos semiabiertos no se solapan); solape parcial de 30 min -> 409.
  - Validaciones: `fin = inicio` -> 400; duracion < 30 min -> 400; duracion > 180 min -> 400;
    franja en el pasado -> 400; cancha inexistente -> 404; sin token -> 401.
  - Fechas: `GET /api/v1/reservas/mias` devuelve exactamente el instante UTC solicitado
    (verificado tras corregir el desfase de zona horaria; ver `L-04` y ADR-0014).

### E-09 - Humo ampliado a 19 pasos sobre el contenedor

- **Comando:** `docker compose -f compose.yml -f compose.mysql.yml build backend` + `up -d backend`
  + `scripts\smoke.ps1 -BaseUrl http://localhost:8089`.
- **Resultado:** **PASS** - `RESULTADO: OK (19/19 pasos)`: 12 de identidad + 7 de venues/reservas
  (alta de establecimiento, alta de cancha, listado, reserva 201, reserva duplicada 409
  `SLOT_UNAVAILABLE`, `GET /reservas/mias`, 401 sin token).

### E-10 - Disponibilidad y cancelacion de reservas

- **Comando:** `backend\mvnw.cmd -B test` (JDK 21 + variables de `.env`).
- **Resultado:** **PASS** - `Tests run: 34, Failures: 0, Errors: 0, Skipped: 0` - `BUILD SUCCESS`
  (`ReservasTest` pasa de 7 a **14 tests**).
- **Disponibilidad** (`GET /api/v1/canchas/{id}/disponibilidad?fecha=&duracionMinutos=`):
  - Sin `horario_cancha` para ese dia se usa la ventana por defecto `06:00-23:00` local ->
    **17 bloques de 60 minutos**, el primero alineado a la hora de apertura y con el
    **precio base** de la cancha (`80.00`). Verificado en test.
  - Reservar el bloque de 12:00 local solo retira ese bloque: los contiguos (11:00 y 13:00)
    siguen ofreciendose (intervalos semiabiertos -> los bloques vecinos no chocan).
  - `400 VALIDATION_ERROR` con: `fecha` ausente, formato invalido, fecha pasada, fecha fuera
    de la ventana de anticipacion y `duracionMinutos` fuera de `30-180`. `404 NOT_FOUND`
    con cancha inexistente. Cancha en `MANTENIMIENTO` -> `200` con `franjas` vacio.
- **Cancelacion** (`POST /api/v1/reservas/{id}/cancelar`):
  - `200` con `estado = CANCELADA`; la franja **vuelve a la disponibilidad** y puede
    reservarse otra vez (201). `historial_reserva` queda con 2 filas (creacion + cancelacion).
  - Segundo intento -> `409 CONFLICT`; reserva de otro usuario -> `403 FORBIDDEN`;
    reserva inexistente -> `404 NOT_FOUND`; sin token -> `401`.
  - `409 CONFLICT` si falta menos que la ventana minima (`cd.reservas.horas-minimas-cancelacion=2`):
    reserva que empieza en 1 hora -> rechazada.

### E-11 - Humo ampliado a 24 pasos (PowerShell y Bash)

- **Comando:**
  `docker compose -f compose.yml -f compose.mysql.yml build backend` + `up -d backend` +
  `scripts\smoke.ps1 -BaseUrl http://localhost:8089` y (Git Bash)
  `bash scripts/smoke.sh http://localhost:8089`.
- **Resultado:** **PASS** - `RESULTADO: OK (24/24 pasos)` en **ambos scripts** (12 de identidad,
  3 de venues, 1 de disponibilidad libre, 3 de reserva, 1 de disponibilidad ocupada,
  2 de cancelacion, 1 de disponibilidad liberada, 1 sin token).
- **Corregido en el camino:** `smoke.sh` fallaba en este equipo porque `python3` es el alias
  del Microsoft Store. Ahora elige el primer lector de JSON que funcione de verdad
  (`jq`, `python3` o `python`) comprobando una invocacion real, no solo su presencia en `PATH`.

### E-12 - Agenda del propietario y bloqueos de cancha (HU-005 + RN-03)

- **Comando:** `backend\mvnw.cmd -B test` (JDK 21 + variables de `.env`).
- **Resultado:** **PASS** - `Tests run: 39, Failures: 0, Errors: 0, Skipped: 0` - `BUILD SUCCESS`
  (`AgendaTest` nuevo con **5 tests**; 14 de `ReservasTest`, 19 de `identity`, 1 de esquema).
- **Horarios** (`GET/POST /api/v1/canchas/{id}/horarios`, `DELETE .../horarios/{id}`):
  - `201` con `{id}` y `Location`; la lectura es **publica** (`200` con `diaSemana`, `horaInicio`
    `06:00:00`, `horaFin`, `precio`, `activo`). Alta sin precio -> rige el precio base.
  - `409 CONFLICT` al solapar otro horario **activo** del mismo dia (mismo rango u 11:00-13:00
    sobre 08:00-12:00); otro dia de la semana si se admite.
  - `400 VALIDATION_ERROR` con `diaSemana` fuera de 1-7, inicio >= fin o precio negativo;
    `403 FORBIDDEN` si el usuario no es el propietario; `401` sin token; `404 NOT_FOUND` si la
    cancha no existe.
  - La baja es **logica** (`activo = 0`): desaparece del catalogo publico, el rango se puede
    volver a registrar y una segunda baja devuelve `404`.
- **Disponibilidad con agenda propia:**
  - Con un horario `14:00-16:00` la disponibilidad ofrece **exactamente 2 bloques** de 60 min y
    el **precio del horario** (50.00), no la ventana por defecto ni el precio base.
  - Una cancha con agenda para un solo dia queda **cerrada** (`franjas` vacio) los demas dias:
    la ventana por defecto solo se usa mientras la cancha **no tenga ningun** horario activo.
- **Bloqueos (RN-03)** (`GET/POST /api/v1/canchas/{id}/bloqueos`, `DELETE .../bloqueos/{id}`):
  - `201` con `{id}`; la franja bloqueada **deja de ofrecerse** y `POST /api/v1/reservas` sobre
    ella devuelve `409 SLOT_UNAVAILABLE`; al levantar el bloqueo la franja vuelve (200) y la
    reserva vuelve a entrar (201).
  - `409 CONFLICT` al solapar otro bloqueo o al intentar bloquear una franja con **reserva
    vigente**; `400` con rango invertido o motivo vacio; `403` para quien no es propietario;
    `401` sin token en el listado (que exige ser propietario); `404` al levantar dos veces.
  - El alta usa `SELECT ... FOR UPDATE` sobre la fila de la cancha (mismo orden de bloqueo que
    RN-01), de modo que una reserva no puede colarse entre la comprobacion y el `INSERT`.

### E-13 - Humo ampliado a 33 pasos (PowerShell y Bash)

- **Comando:**
  `docker compose -f compose.yml -f compose.mysql.yml up -d --build backend` +
  `scripts\smoke.ps1 -BaseUrl http://localhost:8089` y (Git Bash)
  `bash scripts/smoke.sh http://localhost:8089`.
- **Resultado:** **PASS** - `RESULTADO: OK (33/33 pasos)` en **ambos scripts** (24 anteriores +
  4 de horarios: alta 201, listado publico, solape 409, disponibilidad con horario; +
  5 de bloqueos: alta 201, franja no ofrecida, reserva 409, baja 204, franja liberada).
- **Nota:** el horario del humo se registra para el **dia ISO** de la fecha de prueba y con la
  misma ventana `06:00-23:00` que la ventana por defecto, para no alterar las aserciones previas.

### E-14 - Confirmacion y rechazo de reservas por el propietario (HU-008-2)

- **Comando:** `backend\mvnw.cmd -B test` (JDK 21 + variables de `.env`).
- **Resultado:** **PASS** - `Tests run: 45, Failures: 0, Errors: 0, Skipped: 0` - `BUILD SUCCESS`
  (`ReservasTest` pasa de 14 a **20 tests**; 19 de `identity`, 5 de `AgendaTest`, 1 de esquema).
- **Migracion:** `V5__estado_reserva_rechazada.sql` aplicada por Flyway (`now at version v5`) y
  registrada en `flyway_schema_history` (`success = 1`); el `CHECK` de `reserva.estado` admite ya
  `RECHAZADA` (ADR-0018).
- **Bandeja del propietario** (`GET /api/v1/reservas/recibidas`):
  - Devuelve solo las reservas de **sus** canchas (`reserva -> cancha -> establecimiento ->
    propietario`), con filtro opcional `estado` (`?estado=PENDIENTE|CONFIRMADA|...`); un estado
    desconocido devuelve `400 VALIDATION_ERROR` y sin token `401`.
  - Un propietario distinto (con establecimiento y cancha propios) recibe `200` con **0 filas**.
- **Decisiones:**
  - `confirmar` deja la reserva en `CONFIRMADA` y `rechazar` en `RECHAZADA` (con el motivo en
    `historial_reserva.observacion`); ambas exigen ser propietario (`403` si no, `404` si no
    existe) y que la reserva siga `PENDIENTE` y no haya comenzado (`409 CONFLICT`).
  - El historial queda con **2 entradas** (creacion + decision) y un intento rechazado **no**
    deja traza.
  - Rechazar devuelve la franja a la disponibilidad y permite volver a reservarla (`201`);
    `motivo` de mas de 200 caracteres -> `400`.
  - Una reserva insertada directamente con `inicio` en el pasado no se puede atender (`409`).
- **Correccion de aislamiento:** `misReservasConservaLaFranjaSolicitada` usaba el usuario
  compartido de la clase y asumia tener **una** reserva; ahora registra su propio titular, de modo
  que el resultado no depende del orden en que se ejecuten los tests.

### E-15 - Humo ampliado a 39 pasos (PowerShell y Bash)

- **Comando:**
  `docker compose -f compose.yml -f compose.mysql.yml up -d --build mysql backend` +
  `scripts\smoke.ps1 -BaseUrl http://localhost:8089` y (Git Bash)
  `bash scripts/smoke.sh http://localhost:8089`.
- **Resultado:** **PASS** - `RESULTADO: OK (39/39 pasos)` en **ambos scripts** (33 anteriores +
  6 nuevos: bandeja del propietario con la solicitud `PENDIENTE`, confirmar `200 CONFIRMADA`,
  confirmar de nuevo `409`, segunda reserva en la franja contigua `201`, rechazar `200 RECHAZADA`
  y la franja rechazada de vuelta en la disponibilidad).
- **OpenAPI del contenedor:** 22 rutas `/api/v1` (19 anteriores + `recibidas`, `confirmar`,
  `rechazar`).

### E-16 - Cochera opcional con capacidad (RN-08)

- **Comando:** `backend\mvnw.cmd -B test` (JDK 21 + variables de `.env`).
- **Resultado:** **PASS** - `Tests run: 57, Failures: 0, Errors: 0, Skipped: 0` - `BUILD SUCCESS`
  (`CocheraTest` aporta **12 tests** nuevos; 45 previos + 12).
- **Gestion (propietario, solo dueno del establecimiento):**
  `GET/POST /api/v1/establecimientos/{id}/cocheras` y
  `DELETE /api/v1/establecimientos/{id}/cocheras/{idCochera}`: alta con `capacidad` y
  `tarifaHora` (validacion `400`), listado y baja logica; un propietario ajeno recibe `403` y una
  cochera inexistente `404`.
- **Cupo publico** (`GET /api/v1/canchas/{idCancha}/cocheras?inicio=&fin=`): por cada cochera
  activa del establecimiento devuelve `id`, `nombre`, `capacidad`, `cupoDisponible` y `tarifaHora`
  en la franja pedida.
- **Reserva con cochera:** `POST /api/v1/reservas` acepta `cocheraId` **opcional**; la respuesta
  incluye `cocheraId` y `montoCochera`. Coherencia: la cochera debe pertenecer al **mismo
  establecimiento** que la cancha (`400` si no).
- **Cupo por solape:** el cupo vigente cuenta las `reserva_cochera` cuyo padre esta
  `PENDIENTE`/`CONFIRMADA` y cuya franja solapa `[inicio, fin)`; con capacidad 1 la segunda reserva
  solapada falla (`409`) y con capacidad 2 la tercera tambien.
- **Monto:** `montoCochera = tarifa_hora x horas` (`BigDecimal`, `HALF_UP`, 2 decimales).
- **Liberacion:** cancelar o rechazar la reserva libera el cupo **sin** borrar la fila (el padre
  deja de estar vigente).
- **Concurrencia (3/3 repeticiones):** 4 peticiones simultaneas a **canchas distintas** con la misma
  cochera de capacidad 2 -> exactamente **2** reservas `201` y 2 `409`.
- **Correccion de interbloqueo:** el orden de locks paso a ser **cancha -> cochera** y la cochera se
  bloquea **antes** de los `SELECT ... FOR UPDATE` de `reserva`/`bloqueo_cancha`. Antes de fijarlo, el
  orden inverso producia `Deadlock found when trying to get lock` sobre
  `SELECT ... FROM cochera ... FOR UPDATE` (ADR-0019).
- **Nuevo mapper**: `PessimisticLockException` -> `409 RESOURCE_BUSY` (§05.3.10 del prompt).

### E-17 - Humo ampliado a 47 pasos (PowerShell y Bash)

- **Comando:**
  `docker compose -f compose.yml -f compose.mysql.yml up -d --build mysql backend` +
  `scripts\smoke.ps1 -BaseUrl http://localhost:8089` y (Git Bash)
  `bash scripts/smoke.sh http://localhost:8089`.
- **Resultado:** **PASS** - `RESULTADO: OK (47/47 pasos)` en **ambos scripts** (39 anteriores +
  8 de cochera: alta `201`, listado propio, cupo publico `2`, reserva con `cocheraId` `201` con
  `montoCochera = 5.00`, cupo baja a `1`, cancelar `200 CANCELADA`, cupo vuelve a `2` y baja
  logica `204`).
- **Nota:** el humo usa una franja libre (20:00 UTC = 15:00 local) para no alterar las
  aserciones previas.

### E-18 - Pago simulado y reembolso (RN-05, L-12)

- **Comando:** `backend\mvnw.cmd -B test` (JDK 21 + variables de `.env`).
- **Resultado:** **PASS** - `Tests run: 68, Failures: 0, Errors: 0, Skipped: 0` - `BUILD SUCCESS`
  (`PagoTest` aporta **11 tests** nuevos; 57 previos + 11).
- **Modulo nuevo:** `payments` (hexagonal): dominio `Pago`/`Reembolso`, puertos
  `PagoRepositorio`/`PaymentGatewayPort`, servicios `PagoSimuladoService`/
  `PoliticaReembolsoService`, adaptadores `PagoPersistenciaAdapter` (SQL nativo) y
  `SimuladorPagoAdapter`; `PagoResource` expone
  `POST/GET /api/v1/reservas/{idReserva}/pago`. Frontera de pasarela real por
  `PaymentGatewayPort` (REQUISITOS §2: simulador local, pasarela real deshabilitada; ADR-0020).
- **Simulador determinista:** aprueba salvo que `codigoOperacion` empiece por `RECHAZA`;
  el codigo de operacion es el provisto o `SIM-<importe sin punto>`.
- **Pago** (`POST /api/v1/reservas/{idReserva}/pago`, titular; cuerpo
  `{medioPago, codigoOperacion?}`):
  - `201` con `PagoResponse`; **importe = monto_cancha - monto_descuento + monto_cochera**.
  - **RN-05:** como maximo **un pago `APROBADO`** por reserva (indice unico
    `uq_pago_aprobado_reserva` sobre la columna generada `id_reserva_aprobada`); un segundo
    intento tras aprobar devuelve `409`.
  - Un pago `RECHAZADO` puede reintentarse.
  - **El pago no cambia el estado de la reserva:** el propietario sigue confirmando (HU-008-2).
  - `403` si el solicitante no es el titular; `404` si la reserva no existe; `401` sin token.
  - Cada transicion deja traza en `pago_evento`.
- **Reembolso** (politica `PoliticaReembolsoService`, `PoliticaReembolso` como puerto en
  `shared`):
  - **Rechazo por el propietario** -> reembolso **total**.
  - **Cancelacion por el titular** -> reembolso total solo si faltan al menos
    `cd.pagos.horas-cancelacion-gratis` (24 h) para `inicio`; en caso contrario, **ninguno**.
  - El reembolso queda en `PROCESADO` y el pago pasa a `REEMBOLSADO`.
  - **Idempotente:** solo actua sobre un pago `APROBADO`; el reembolso corre en la **misma
    transaccion** que el cambio de estado de la reserva (sin ciclo de dependencias entre
    modulos).
  - `GET /api/v1/reservas/{idReserva}/pago` -> `200` con el pago y `reembolsos[]`.

### E-19 - Humo ampliado a 53 pasos (PowerShell y Bash)

- **Comando:**
  `docker compose -f compose.yml -f compose.mysql.yml up -d --build mysql backend` +
  `scripts\smoke.ps1 -BaseUrl http://localhost:8089` y (Git Bash)
  `bash scripts/smoke.sh http://localhost:8089`.
- **Resultado:** **PASS** - `RESULTADO: OK (53/53 pasos)` en **ambos scripts** (47 anteriores +
  6 de pago/reembolso: pago `201 APROBADO`, importe con cochera, `GET` del pago `200`, segundo
  pago `409` por RN-05, cancelacion con reembolso total y verificacion del reembolso
  `PROCESADO` + pago `REEMBOLSADO`).
- **`bash -n scripts/smoke.sh`:** OK (sintaxis valida).

### E-20 - Liquidacion de ingresos del propietario (CU04.8/CU05.2/CU05.4, RN-07)

- **Comando:** `backend\mvnw.cmd -B test` (JDK 21 + variables de `.env`).
- **Resultado:** **PASS** - `Tests run: 78, Failures: 0, Errors: 0, Skipped: 0` - `BUILD SUCCESS`
  (`LiquidacionTest` aporta **10 tests** nuevos; 68 previos + 10).
- **Modulo nuevo:** `settlements` (hexagonal): dominio `Liquidacion` (cabecera) y
  `DetalleLiquidacion` (linea), puerto `LiquidacionRepositorio`, servicio `LiquidacionService`,
  adaptador de persistencia (SQL nativo) y `LiquidacionResource`
  (`POST/GET /api/v1/liquidaciones`, `GET .../liquidaciones/{id}`). Tablas `liquidacion` y
  `detalle_liquidacion` (V1) reutilizadas; ADR-0021.
- **Que se liquida:** reservas del propietario con **pago `APROBADO`**, estado **`CONFIRMADA`/
  `FINALIZADA`**, cuya fecha de inicio cae en el periodo y que aun no estan en `detalle_liquidacion`.
  El monto por reserva es `pago.monto` (incluye la cochera opcional).
- **Calculo:** comision `cd.liquidaciones.porcentaje-comision` (10 % por defecto): por linea
  `monto_comision = round(monto_reserva x pct / 100, 2)` y `monto_neto = monto_reserva - monto_comision`;
  la cabecera suma las lineas. Con `monto 80.00` -> bruto `80.00`, comision `8.00`, neto `72.00`.
- **RN-07 (una reserva, una liquidacion):** la generacion bloquea la fila del `propietario`
  (`FOR UPDATE`) antes de leer las reservas liquidables y excluye las ya liquidadas; el
  `UNIQUE(id_reserva)` de `detalle_liquidacion` es la garantia final. Una segunda generacion del
  mismo periodo no vuelve a liquidar la reserva (`409`, 1 sola linea en BD).
- **Exclusiones verificadas:** reserva sin pago -> `409`; reserva pagada pero aun `PENDIENTE`
  (sin confirmar) -> `409`; reserva pagada y **cancelada** (pago fuera de la ventana gratuita, que
  queda `APROBADO`) -> `409`.
- **Autorizacion:** el propietario se toma del `sub` del JWT; liquidacion ajena -> `403`; inexistente
  -> `404`; sin token -> `401`; usuario sin perfil de propietario -> `409`; periodo invalido -> `400`.

### E-21 - Humo ampliado a 59 pasos (PowerShell y Bash)

- **Comando:**
  `docker compose -f compose.yml -f compose.mysql.yml up -d --build mysql backend` +
  `scripts\smoke.ps1 -BaseUrl http://localhost:8089` y (Git Bash)
  `bash scripts/smoke.sh http://localhost:8089`.
- **Resultado:** **PASS** - `RESULTADO: OK (59/59 pasos)` en **ambos scripts** (53 anteriores +
  6 de liquidacion: reserva pagada+confirmada, alta de liquidacion `201` con comision 10 %, segunda
  generacion `409` por RN-07 y `GET` del detalle `200`).
- **`bash -n scripts/smoke.sh`:** OK.

### E-22 - Expiracion del hold de pago y revocacion (ADR-0022)

- **Comando:** `backend\mvnw.cmd -B test` -> **`Tests run: 85, Failures: 0, Errors: 0` - BUILD SUCCESS**.
- **`ExpiracionReservasTest` (4 tests):** una reserva `PENDIENTE` sin pago cuyo hold vencio pasa a
  `EXPIRADA`, libera la franja y deja traza (`2` entradas de historial); una reserva **dentro** del hold
  sigue `PENDIENTE`; una reserva **con pago `APROBADO`** no expira aunque venza el hold; una reserva
  respeta el `hold_pago_min` de su establecimiento (`180` min no vence a los `60`).
- **`ReservasTest` (+3 tests de revocacion, 23 en total):** el propietario revoca una `CONFIRMADA` ->
  `RECHAZADA`, la franja vuelve a ofrecerse y el motivo queda en la traza (`3` entradas); revocar una
  reserva no confirmada -> `409` (y `409` si se repite); revocar reservas ajenas -> `403`; inexistente
  -> `404`; sin token -> `401`.
- **Reloj:** la antiguedad se mide contra `NOW(6)` (reloj de la base, el mismo que llena
  `fecha_creacion`), de modo que el resultado no depende de la zona del servidor (L-04).

### E-23 - Humo ampliado a 63 pasos (PowerShell y Bash)

- **Comando:**
  `docker compose -f compose.yml -f compose.mysql.yml up -d --build mysql backend` +
  `scripts\smoke.ps1 -BaseUrl http://localhost:8089` y (Git Bash)
  `bash scripts/smoke.sh http://localhost:8089`.
- **Resultado:** **PASS** - `RESULTADO: OK (63/63 pasos)` en **ambos scripts** (59 anteriores + 4 de
  revocacion: reserva confirmada, revocacion `200 RECHAZADA` y segundo intento `409`).
- **`bash -n scripts/smoke.sh`:** OK.

### E-24 - Cierre automatico de reservas concluidas (ADR-0023)

- **Comando:** `backend\mvnw.cmd -B test` -> **`Tests run: 89, Failures: 0, Errors: 0` - BUILD SUCCESS**.
- **`FinalizacionReservasTest` (4 tests, 85 anteriores + 4):** una reserva `CONFIRMADA` cuyo `fin` ya paso
  pasa a `FINALIZADA` sin tocar el importe y deja traza (`3` entradas de historial: creacion, confirmacion
  y finalizacion); una `CONFIRMADA` **vigente** sigue `CONFIRMADA`; una `PENDIENTE` con `fin` pasado **no**
  se finaliza (su cierre lo hace la expiracion del hold); el lote no arrastra reservas vigentes.
- **Reloj:** `inicio`/`fin` se guardan como literales UTC (ADR-0014), por lo que el "ya concluyo" se mide
  contra `UTC_TIMESTAMP(6)` y no `NOW(6)` (hora local, L-04): el resultado no depende de la zona del
  servidor.
- **Cobertura de humo:** el job no tiene superficie HTTP (lo dispara el temporizador), por lo que no se
  cubre en `smoke.ps1`/`smoke.sh`.

### E-25 - Calificaciones de reservas finalizadas (ADR-0024)

- **Comando:** `backend\mvnw.cmd -B test` -> **`Tests run: 96, Failures: 0, Errors: 0` - BUILD SUCCESS**.
- **`CalificacionTest` (7 tests, 89 anteriores + 7):** una reserva `FINALIZADA` se califica (`201`) y el
  listado publico de la cancha devuelve `total=1`, promedio y la calificacion; una reserva aun vigente
  responde `409`; la segunda calificacion de la misma reserva responde `409` (RN-04); otro usuario
  recibe `403`; puntaje `6` recibe `400`; reserva/cancha inexistente `404`; dos calificaciones 5 y 4
  dan promedio `4.5`.
- **Preparacion:** cada prueba crea su propia cancha; la reserva se lleva a `FINALIZADA` cerrando su
  franja por SQL y ejecutando `FinalizarReservasUseCase` (el temporizador esta apagado en `test`).
- **Cobertura de humo:** **RUN** - `scripts/smoke.ps1 -BaseUrl http://localhost:8089 -DbPort 3309`
  y `DB_PORT=3309 bash scripts/smoke.sh http://localhost:8089` -> **`RESULTADO: OK (69/69 pasos)`**
  en ambos (63 anteriores + 6 de calificaciones: alta de reserva, confirmacion, cierre por el job,
  `201` al calificar, `409` al repetir y listado publico con `total=1`/`promedio=5`). El humo
  retrasa la franja por SQL (la API no permite reservar en el pasado) y espera al job; requiere el
  cliente `mysql` y credenciales de `.env`, y se omite con `[SKIP]` si faltan.
- **Riesgo abierto:** la unicidad concurrente no tiene mapper; la peticion perdedora podria recibir
  `500` en vez de `409`.

### E-26 - Reclamos de soporte y avisos in-app (ADR-0025)

- **Comando:** `backend\mvnw.cmd -B test` -> **`Tests run: 104, Failures: 0, Errors: 0` - BUILD SUCCESS**.
- **`ReclamoNotificacionTest` (8 tests, 96 anteriores + 8):** crea y lista un reclamo `ABIERTO`;
  lo asocia a una reserva propia (`403` con reserva ajena, `404` con reserva inexistente); valida
  asunto/descripcion (`400`); el detalle devuelve los seguimientos anadidos; `403` al ver o
  comentar un reclamo ajeno; `409` al comentar uno `CERRADO`; la confirmacion de una reserva genera
  el aviso "Reserva confirmada" para el titular y marcar el aviso como leido baja `noLeidas` en 1
  (`404` si lo intenta otro usuario).
- **Emision:** ademas de confirmar, los servicios de atencion (rechazar/revocar), el job de cierre
  y el pago aprobado emiten aviso a traves del puerto compartido `AvisosUsuario`.
- **Cobertura de humo:** **RUN** - `smoke.ps1` y `smoke.sh` -> **`RESULTADO: OK (75/75 pasos)`**
  (69 anteriores + 6: alta de reclamo, bandeja, seguimiento, detalle, bandeja de avisos y marcar
  como leido).
- **Fuera de alcance:** moderacion de reclamos por la administracion (cambio de estado y
  respuesta oficial); ver `L-16`.

### E-27 - Moderacion de reclamos por `PLATFORM_ADMIN` (ADR-0026)

- **Comando:** `backend\mvnw.cmd -B test` -> **`Tests run: 109, Failures: 0, Errors: 0` - BUILD SUCCESS**.
- **`ModeracionReclamoTest` (5 tests, 104 anteriores + 5):** un token `USER` recibe `403` en
  `/api/v1/admin/reclamos`; el administrador lista los reclamos y filtra por estado (desconocido
  `400`); `ABIERTO -> EN_REVISION` con comentario deja el seguimiento y emite "Reclamo actualizado"
  al titular; `CERRADO` es terminal y repetir el estado da `409`; la respuesta oficial (`201`)
  emite "Respuesta a tu reclamo", un reclamo inexistente da `404` y responder uno cerrado, `409`.
- **Rol en el token:** las pruebas registran el usuario, insertan `usuario_rol` y vuelven a iniciar
  sesion; `/api/v1/users/me` confirma `roles = [PLATFORM_ADMIN]`.
- **Cobertura de humo:** **RUN** - ambos scripts -> **`RESULTADO: OK (81/81 pasos)`** (75 anteriores
  + 6: listado admin, `403` con token `USER`, cambio de estado, respuesta oficial, detalle y avisos).
  La promocion a administrador se hace por SQL, por lo que estos pasos se omiten con `[SKIP]` si
  falta el cliente `mysql` o las credenciales.

### E-28 - Administracion de plataforma: propietarios, supervision y auditoria (ADR-0027)

- **Comando:** `backend\mvnw.cmd -B test` -> **`Tests run: 113, Failures: 0, Errors: 0` - BUILD SUCCESS**.
- **`AdminPlataformaTest` (4 tests, 109 anteriores + 4):** un token `USER` recibe `403` en
  propietarios, establecimientos y auditoria; el administrador valida un propietario `PENDIENTE`
  (`200 VALIDADO`), el dueno recibe "Propietario validado" y la auditoria registra
  `PROPIETARIO_VALIDADO` con el `idEntidad` correcto; rechazar sin motivo da `400`, con motivo
  `200 RECHAZADO` + aviso con el motivo y luego puede revalidarse; la supervision devuelve el
  establecimiento con su numero de canchas, la reserva con el titular y el pago con
  `reembolsado = 0` y estado `APROBADO`.
- **Auditoria de moderacion:** `ModerarReclamosService` ahora registra `RECLAMO_<ESTADO>` y
  `RECLAMO_RESPUESTA` via el puerto compartido `RegistroAuditoria`.
- **Cobertura de humo:** **RUN** - ambos scripts -> **`RESULTADO: OK (87/87 pasos)`** (81 anteriores
  + 6: validacion de propietario, aviso, establecimientos, reservas, pagos y auditoria). Se omiten
  con `[SKIP]` si falta MySQL porque la promocion a administrador se hace por SQL.

### E-29 - Gestion de usuarios, roles y estado de cuenta (ADR-0028)

- **Comando:** `backend\mvnw.cmd -B test` -> **`Tests run: 117, Failures: 0, Errors: 0` - BUILD SUCCESS**.
- **`AdminUsuariosTest` (4 tests, 113 anteriores + 4):** un token `USER` recibe `403`; el listado
  filtra por correo, estado y rol (valores desconocidos `400`) y el detalle devuelve el usuario;
  cambiar los roles a `[USER, OWNER]` responde `200`, el token viejo conserva `[USER]` y tras
  re-login llega `OWNER`, con aviso "Tus roles cambiaron" y auditoria `USUARIO_ROLES_CAMBIADOS`;
  roles vacios o desconocidos dan `400`; desactivar la cuenta devuelve `200`, el login responde
  `403`, repetir el estado `409` y reactivar permite entrar de nuevo, con auditoria
  `USUARIO_ESTADO_CAMBIADO`.
- **Cobertura de humo:** **RUN** - ambos scripts -> **`RESULTADO: OK (91/91 pasos)`** (87 anteriores
  + 4: listado de usuarios, cambio de roles, desactivacion y login rechazado).
- **Riesgo abierto:** los tokens vigentes no se revocan al cambiar rol/estado (`L-20`).

### E-30 - Administracion de cocheras y promociones (ADR-0029)

- **Comando:** `backend\mvnw.cmd -B test` -> **`Tests run: 121, Failures: 0, Errors: 0` - BUILD SUCCESS**.
- **`AdminCocherasPromocionesTest` (4 tests, 117 anteriores + 4):** un token `USER` recibe `403`;
  se crea una promocion `PORCENTAJE` asociada a una cancha (`201 ACTIVA`, `canchas[0]` correcta),
  aparece al filtrar por `ACTIVA`, se desactiva (`200`) y repetir da `409`, con auditoria
  `PROMOCION_INACTIVA`; el alta valida codigo duplicado (`409`), tipo desconocido (`400`),
  porcentaje > 100 (`400`), fechas invertidas (`400`) y cancha inexistente (`404`); la supervision
  de cocheras lista la nueva cochera `ACTIVA`, la desactiva (`200`), repetir da `409` y una cochera
  inexistente `404`.
- **Cobertura de humo:** **RUN** - ambos scripts -> **`RESULTADO: OK (96/96 pasos)`** (91 anteriores
  + 5: alta de promocion, listado, baja, listado de cocheras y baja de una cochera nueva; la
  cochera del flujo de reservas se elimina antes).
- **Fuera de alcance:** aplicar la promocion al precio de la reserva y el alta de promociones por
  el propietario (CU04.6); ver `L-21`.

### E-31 - Promociones del propietario aplicadas a la reserva (ADR-0030)

- **Comando:** `backend\mvnw.cmd -B test` -> **`Tests run: 125, Failures: 0, Errors: 0` - BUILD SUCCESS**.
- **`PromocionReservaTest` (4 tests, 121 anteriores + 4):** el propietario crea y lista su promocion
  (un token ajeno recibe `403`); un `PORCENTAJE` del 10% sobre una cancha de 80 deja
  `montoDescuento = 8.00` y el pago cobra `72.00`; un `MONTO` de 100 se limita al precio (70.00); y
  un codigo inexistente, inactivo o asociado a otra cancha responde `409`.
- **Vigencia por fecha de reserva:** la prueba del porcentaje crea la promocion con
  `fechaInicio = manana` y reserva a +3 dias, verificando que se evalua contra la franja y no contra
  el dia actual.
- **Cobertura de humo:** **RUN** - ambos scripts -> **`RESULTADO: OK (98/98 pasos)`** (96 anteriores
  + 2: alta de la promocion del propietario y reserva con `codigoPromocion` y descuento `8.00`).
- **Limite conocido:** no se valida solapamiento de promociones; el usuario elige un codigo.

### E-32 - Catalogo avanzado: ubicaciones, filtros y ficha de cancha (ADR-0031)

- **Comando:** `backend\mvnw.cmd -B test` -> **`Tests run: 129, Failures: 0, Errors: 0` - BUILD SUCCESS**.
- **`CatalogoCanchaTest` (4 tests, 125 anteriores + 4):** los catalogos publicos responden
  (distritos con provincia/departamento, deportes y caracteristicas); el propietario asigna
  deporte, caracteristica y foto principal, y la ficha las devuelve junto con distrito y
  reputacion (`total = 0`), mientras otro usuario recibe `403`; el listado filtra por deporte,
  `precioMax` y texto (una cancha de 120 queda fuera con `precioMax = 100`); un deporte inexistente
  da `400`, repetir el orden de foto `409`, borrar la foto `204` y repetir `404`.
- **Cobertura de humo:** **RUN** - ambos scripts -> **`RESULTADO: OK (104/104 pasos)`** (98 anteriores
  + 6: distritos, deportes, `PUT` de deportes, alta de foto, ficha y listado filtrado).
- **Fuera de alcance entonces:** comparar canchas (CU02.2) y edicion/baja de establecimiento/cancha
  (cubiertos despues en E-33).

### E-33 - Edicion (CU04.1), baja logica (CU04.2) y comparacion de canchas (CU02.2, ADR-0032)

- **Comando:** `backend\mvnw.cmd -B test` -> **`Tests run: 135, Failures: 0, Errors: 0` - BUILD SUCCESS**.
- **`EdicionYBajaVenuesTest` (6 tests, 129 anteriores + 6):** `PUT` de establecimiento y cancha
  actualiza nombre/direccion/distrito/descripcion/capacidad/precio (la ficha y el listado lo
  reflejan); otro usuario recibe `403` en ambos `PUT` y en ambas bajas; precio negativo `400`,
  nombre de cancha repetido `409` y cancha inexistente `404`; la baja de una cancha la saca del
  listado, su ficha queda `INACTIVA`, la disponibilidad no ofrece franjas y reservar da `409
  SLOT_UNAVAILABLE`, con segundo `DELETE` `404`; la baja del establecimiento desactiva sus dos
  canchas; con una reserva `PENDIENTE` futura ambas bajas dan `409` y funcionan tras cancelarla;
  `GET /canchas/comparar` devuelve 2 fichas en el orden pedido y responde `400` (una sola cancha,
  repetida, id no numerico) y `404` (inexistente).
- **Cobertura de humo:** **RUN** - ambos scripts -> **`RESULTADO: OK (112/112 pasos)`** (104
  anteriores + 8: alta de complejo y cancha de gestion, `PUT` de ambos, comparacion de 2 fichas,
  baja de cancha, ficha `INACTIVA` y baja del complejo).
- **Fuera de alcance:** reactivacion de un recurso dado de baja (solo SQL) y envio de la
  comparacion como PDF/imagen (el endpoint entrega las fichas para que el frontend arme la vista).

### E-34 - Identidad completa: verificacion de correo, perfil y solicitud de propietario (ADR-0033)

- **Comando:** `backend\mvnw.cmd -B test` -> **`Tests run: 144, Failures: 0, Errors: 0` - BUILD SUCCESS**.
- **`PerfilVerificacionPropietarioTest` (4 tests):** el registro emite `EMAIL_VERIFY`; verificar
  marca `correoVerificado=true`, el token no se reutiliza y uno invalido da `400`; el reenvio emite
  uno nuevo, invalida el anterior y devuelve `202` para correos inexistentes sin emitir token;
  `PUT /users/me` actualiza nombres/apellidos/telefono (`400` en blanco, `401` sin token);
  `POST /propietarios/solicitudes` crea `PENDIENTE` (repetir con el mismo documento devuelve la
  existente), `GET /propietarios/me` la consulta, documento ajeno `409` y cuenta sin solicitud `404`.
- **Cobertura de humo:** 112/112 (la verificacion de correo no se ejercita en el humo porque el
  envio es simulado y el token solo aparece en el log; cubierta por tests).

### E-35 - Idempotencia y reprogramacion de reservas (ADR-0034)

- **`IdempotenciaModificacionTest` (2 tests):** dos `POST /reservas` con la misma
  `Idempotency-Key` devuelven el mismo `id` y una sola reserva; otra clave crea otra; clave de mas
  de 80 caracteres `400`; reprogramar a una nueva franja conserva duracion e importes, libera la
  franja anterior para otro titular (`201`), cambiar la duracion `400`, otro usuario `403` y una
  reserva cancelada `409`.
- **Cobertura de humo:** 112/112 (los flujos nuevos se validan en la suite; el humo mantiene su
  alcance estable).

### E-36 - Endurecimiento: limite de intentos y revocacion de tokens (ADR-0033)

- **`EndurecimientoAccesoTest` (3 tests):** cinco fallos bloquean el correo (`429
  TOO_MANY_ATTEMPTS`, incluso con la clave correcta) sin afectar a otras cuentas; cambiar la
  contrasena invalida el access token vigente (`401 UNAUTHENTICATED`).
- **`AdminUsuariosTest` ajustado a L-20:** el token previo al cambio de roles pasa a `401` y el
  nuevo login trae los roles actualizados.
- **Estabilidad de la suite:** los 16 helpers `documentoUnico()` usan ahora un aleatorio de 18
  digitos (`ThreadLocalRandom`) en lugar de `nanoTime() % 10M`, que colisionaba con documentos de
  ejecuciones previas contra la base compartida y producia fallos esporadicos (`409` al crear el
  establecimiento de prueba).
- **Cobertura de humo:** 112/112 con re-login tras el cambio de roles.



## Pendientes (NOT RUN)

| Area | Estado |
|---|---|
| Concurrencia de reservas (20-50 hilos) | **RUN** (E-08: 30 hilos -> 1 reserva; 3/3 repeticiones) |
| Disponibilidad por fecha | **RUN** (E-10: ventana por defecto, descuento de reservas, 400/404) |
| Cancelacion de reservas | **RUN** (E-10: ventana minima, 403/404/409, liberacion de la franja) |
| Auth / JWT / ownership (IDOR) | **RUN** (JWT/refresh, ownership de venues/reservas/horarios/bloqueos, rate limiting `S-4` y revocacion `L-20` en E-34/E-36; verificacion de correo `S-5`) |
| Horarios del propietario (HU-005) | **RUN** (E-12: alta/listado/baja, solapes 409, disponibilidad con agenda) |
| Bloqueos de cancha (RN-03) | **RUN** (E-12: alta bajo lock, cruce con reservas y bloqueos, liberacion) |
| Cochera (RN-08) | **RUN** (E-16: `cochera` con capacidad/tarifa, reserva con `cocheraId` opcional, cupo por solape 1/2, coherencia de establecimiento, liberacion al cancelar y 4 canchas concurrentes -> exactamente 2; E-17: humo 47/47) |
| Confirmacion/rechazo de reserva por el propietario (HU-008-2) | **RUN** (E-14/E-15: bandeja, confirmar/rechazar, 403/404/409, liberacion de la franja) |
| Un pago aprobado por reserva (RN-05) | **RUN** (E-18: `uq_pago_aprobado_reserva`, segundo pago `409`; E-19: humo) |
| Una reserva en una sola liquidacion (RN-07) | **RUN** (E-20: `UNIQUE` + exclusion de reservas ya liquidadas y lock del propietario; E-21: humo) |
| Pagos/reembolsos | **RUN** (E-18: pago simulado determinista, importe con cochera, reembolso total por rechazo/ventana y `REEMBOLSADO`; E-19: humo 53/53) |
| Liquidaciones/comisiones | **RUN** (E-20: comision 10 %, calculo bruto/comision/neto, exclusiones y autorizacion; E-21: humo 59/59) |
| Expiracion del hold de pago | **RUN** (E-22: expira vencida sin pago, no expira dentro del hold ni con pago aprobado, respeta el hold del establecimiento) |
| Revocacion de una reserva confirmada (L-14) | **RUN** (E-22: `CONFIRMADA` -> `RECHAZADA` con liberacion de franja y traza, `403/404/409`; E-23: humo 63/63) |
| Cierre automatico de reservas concluidas (L-15) | **RUN** (E-24: `CONFIRMADA` vencida -> `FINALIZADA` con traza, no finaliza vigentes ni pendientes, reloj UTC) |
| Calificaciones (F06, RN-04) | **RUN** (E-25: alta/listado publico con promedio, `403/404/409/400`, doble calificacion `409`; humo 69/69) |
| Reclamos y avisos (F06, CU06.6) | **RUN** (E-26: reclamo `ABIERTO`, `403` ajeno, seguimientos, `409` cerrado, avisos `noLeidas`/`leida`; humo 75/75) |
| Moderacion de reclamos (F07, CU06.6) | **RUN** (E-27: `PLATFORM_ADMIN` `403` a USER, listado/filtros, transiciones con `409`, respuesta oficial y avisos; humo 81/81) |
| Administracion de plataforma (F07) | **RUN** (E-28: validacion de propietarios con aviso y auditoria, supervision de establecimientos/reservas/pagos; humo 87/87) |
| Gestion de usuarios y roles (F07) | **RUN** (E-29: filtros, cambio de roles con re-login, desactivacion con login `403`, auditoria; humo 91/91) |
| Cocheras y promociones (F07, CU06.5) | **RUN** (E-30: alta/listado/baja de promocion con auditoria y validaciones, supervision/baja de cochera; humo 96/96) |
| Promociones aplicadas (CU04.6) | **RUN** (E-31: alta por propietario, 10% -> descuento 8.00 y pago 72.00, tope al precio, `409` por codigo invalido; humo 98/98) |
| Catalogo avanzado (F02/F03) | **RUN** (E-32: distritos/deportes/caracteristicas, ficha con fotos y reputacion, filtros, `403` ajeno y validaciones; humo 104/104) |
| Edicion y baja de venues (CU04.1/CU04.2) | **RUN** (E-33: `PUT` de establecimiento/cancha con ownership, baja logica de cancha y complejo con `409` si hay reservas vigentes, `404/403/409/400`; humo 112/112) |
| Comparar canchas (CU02.2) | **RUN** (E-33: 2 a 4 fichas en el orden pedido, `400` por seleccion invalida y `404` si falta una cancha; humo 112/112) |
| Identidad completa (CU01.2/CU01.5/CU01.6) | **RUN** (E-34: verificacion de correo con token de un solo uso, reenvio, perfil editable y solicitud de propietario; suite 144/144) |
| Idempotencia y reprogramacion (CU03.2/CU03.7) | **RUN** (E-35: misma `Idempotency-Key` -> misma reserva; reprogramacion con duracion fija y liberacion de franja; `400/403/409`) |
| Endurecimiento de acceso (S-4/L-20) | **RUN** (E-36: `429` tras 5 fallos, revocacion de access tokens por version; suite 144/144 y humo 112/112) |
| E2E UI (Playwright) | **RUN** (E-37: flujo usuario registro->login->catálogo paginado->detalle rediseñado con calendario semanal y navegación->reserva->pago->reclamo->responsive; flujo propietario/admin validación OWNER->establecimiento->cancha->fotos->cochera->agenda->promos->supervisión; dev 4200/4300 y Docker 8083; capturas en `docs/evidencias/ui`) |
| Frontend unitario (Vitest) | **RUN** (E-38: 15/15; `ApiError`, guards, paletas, comparacion, App; build Angular 22 OK) |
| Edicion/baja de establecimiento + reenvio de verificacion (UI) | **RUN** (E-39: `GET /establecimientos/{id}` con ownership (200/403/404), formulario de edicion y baja desde el panel, boton de reenvio en el perfil; 144/144 backend, build OK y E2E propietario/admin 20/20) |
| Paginacion y modal de roles (UI) | **RUN** (E-40: paginador reutilizable en usuarios, propietarios, reclamos, promociones, cocheras, supervision, auditoria, reservas, liquidaciones, notificaciones y reclamos propios; editor de roles en modal CDK accesible; E2E usuario OK y E2E propietario/admin OK; humo 112/112) |
| Accesibilidad de teclado y contraste | **RUN** (E-41: primer Tab al skip link, foco visible 3 px, contraste de texto atenuado 5.44:1 y boton primario 15.39:1, foco atrapado en modal de roles por 15 Tabs y cierre con Escape, paginador con `aria-current` y rango visible; perfil de administrador sin solicitud de propietario) |
| Revision manual de estructura y teclado | **RUN** (E-42: 13 rutas con un solo `h1`, sin saltos de encabezado, sin controles sin nombre, sin campos sin etiqueta, sin imagenes sin `alt`, sin ids duplicados, `main` y skip link presentes; 25 Tabs sin perder foco; capturas `a11y-*.png`; corregido `h3` en estados vacios) |
| Fase 1 de mejoras (UI/flujo) | **RUN** (E-43: `GET /establecimientos/mios` con propio incluido/ajeno excluido; panel descubre el local tras borrar `localStorage`; registro auto-login y retorno a `/mis-reservas`; paginador estable; vista previa en la misma pestaña con retorno a la cancha en edición; galería con todas las miniaturas, visor, carga real de imágenes y fallback; migración V11; textos de horario semanal/bloqueos. Backend 144/144, humo 112/112, unitarios 15/15, E2E usuario 14/14, E2E propietario/admin 28/28, a11y OK) |
| Rediseño de fusión (UI) | **RUN** (E-44: tokens bosque/lima y fuentes locales; shell, home, catálogo, detalle y login rediseñados; carrusel, calendario semanal, precios y comparación conservados; build OK, 15/15 unitarios, E2E usuario 14/14, E2E propietario/admin 28/28, a11y OK) |
| Favoritos, catálogo enriquecido y paneles con sidebar | **RUN** (E-45: endpoints de favoritos persistidos; catálogo con distrito, foto, reputación y número de opiniones; botón comparar; página `/favoritos`; iconos SVG; sidebars de cliente, propietario y administración. Backend 146/146, humo 112/112, build OK, E2E usuario 15/15, E2E propietario/admin 28/28, a11y 14 rutas OK) |
| Topbar standalone, Actividades, Cómo funciona, servicios demo y sidebars | **RUN** (E-46: header con Inicio/Espacios/Actividades/Cómo funciona y menú Mi cuenta; páginas públicas de Actividades y Cómo funciona; servicios incluidos con datos demo V13; sidebar con espacio de trabajo, ayuda y volver al sitio en cliente/propietario/administración. Backend 146/146, humo 112/112, build OK, E2E usuario 15/15, E2E propietario/admin 28/28, a11y 16 rutas OK) |
| Configuración persistente de plataforma (Bloque C) | **RUN** (E-47: `V17` siembra comisión, ventanas de cancelación, anticipación y correo de soporte; `GET/PUT /admin/configuracion` valida rangos y audita; el puerto `ConfiguracionPlataforma` se lee en cada operación; `/admin/configuracion` editable. Backend 158/158, humo 135/135 PS y 116/116 Bash, build OK, 22/22 unitarios, E2E usuario 20/20, propietario/admin 37/37, a11y 27 rutas OK) |
| Rol OPERADOR y portal de recepción (Bloque D) | **RUN** (E-48: `V18` agrega el rol `OPERADOR` y `operador_establecimiento`; alcance por sede en SQL (ADMIN todas, OPERADOR asignadas, OWNER propias) con `403` sin rol; agenda, resumen, reserva presencial (cliente registrado + cancha de sede operada), check-in `CONFIRMADA -> FINALIZADA` en la ventana de 2 h, cobro presencial `APROBADO` único (RN-05) e incidencias sobre `reclamo`; `GET/POST/DELETE /admin/establecimientos/{id}/operadores`, `/admin/operadores` y `/recepcion`. Backend 160/160 (`RecepcionTest` 2/2), humo 153/153 PS y 134/134 Bash, build OK, 22/22 unitarios, E2E usuario OK, propietario/admin OK, a11y 34 rutas OK) |
| Calendario derivado del horario configurado | **RUN** (E-50: la rejilla "Disponibilidad semanal" de la ficha toma las filas del horario configurado (`GET /canchas/{id}/horarios`) partido por la duración, así que una hora ocupada o bloqueada queda como "Sin cupo" en vez de desaparecer. Verificado con datos reales: cancha nueva con horario 08:00-15:00 y bloqueo 10:00-11:00 → la API pasa de 7 a 6 franjas libres, la tabla mantiene las 7 filas con la de 10:00 como "Sin cupo", 6 celdas libres, los días sin agenda se rotulan "Cerrado" y el listado redundante "Horario semanal" ya no existe. Las horas se formatean en `America/Lima` también en el panel de reserva. Build OK, 22/22 unitarios, E2E usuario OK, propietario/admin OK, a11y 34 rutas OK; ADR-0041) |
| Badge de notificaciones y modo claro/oscuro | **RUN** (E-49: `NotificacionesStore` comparte el conteo `noLeidas`; el badge aparece en la campana de la topbar publica y de la cabecera de cuenta con tope `9+` y `aria-label` con el numero, y `/notificaciones` escribe en el store para no desincronizar (verificado E2E: 2 -> marcar una -> 1 -> marcar todas -> desaparece). `ThemeService` agrega `modo` (`claro|oscuro`) persistido en `cd.modo`, aplica `data-theme='dark'` + `color-scheme` y por defecto sigue `prefers-color-scheme`; los tokens oscuros se declaran al final de `_tokens.scss` y se migraron a tokens 98 colores fijos de superficies, bordes y textos en componentes de pagina y en el chrome de los cuatro portales. Build OK, 22/22 unitarios, E2E usuario OK, propietario/admin OK, a11y 34 rutas OK; capturas `docs/evidencias/ui/{claro,oscuro}-*-1440.png`) |
| Bloqueos de cancha desde recepción | **RUN** (E-51: `VenuesRepositorio.esGestorDeCancha` acepta al propietario o al personal asignado a la sede (`operador_establecimiento`, V18), y `GestionAgendaService` lo usa en los bloqueos manteniendo al propietario como único gestor de horarios y precios. El portal de recepción reutiliza `/canchas/{id}/bloqueos` con el token del operador y la pantalla "Agenda del día" suma la sección "Bloqueos de la fecha" (listado por sede y fecha, alta con cancha/desde/hasta/motivo y baja con confirmación). `RecepcionTest` cubre el alta/baja por el operador y el `403` en cancha de otra sede, al crear horarios y con un `USER` sin asignación; E2E propietario/admin bloquea y levanta una franja desde la UI (captura `recepcion-bloqueo-1440.png`). Backend 160/160, build OK, 22/22 unitarios, E2E usuario OK, propietario/admin OK, a11y 34 rutas OK, humo 153/153 PS y 134/134 Bash; ADR-0042) |
| Testcontainers | PENDIENTE (Docker ya operativo; migrar los tests, `L-09`) |
| Rendimiento / seguridad automatizada | NOT RUN |
