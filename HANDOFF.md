# HANDOFF - CanchaDirecta

> Documento de continuidad. Permite retomar el proyecto **sin conversacion previa**.
> Actualizar tras cada hito significativo.

## 0. Estado global

- **Estado:** **BACKEND + FRONTEND OPERATIVOS**. Backend 144/144 tests y 112/112 humo;
  frontend Angular 22 compila, pasa 15/15 tests unitarios y 2 suites E2E reales (usuario y
  propietario/admin) contra backend, tanto en dev como en el contenedor Nginx (`8083`). Detalle de
  cancha rediseñado con calendario semanal. Queda pulido de UI secundario, accesibilidad manual y
  evidencia formal. Fase 0 y Fase 2 completadas.
- **Backend completado y verificado** (modulos `shared`, `identity`, `venues`, `bookings`,
  `payments`, `settlements`, `engagement`, `platform`): identidad completa (registro, verificacion
  de correo, login con limite de intentos, refresh, logout, recuperacion, perfil editable y
  solicitud de propietario), establecimientos y canchas, catalogo (filtros, ficha con
  fotos/reputacion, comparacion), disponibilidad, reservas con locking + idempotencia +
  reprogramacion, cochera opcional (RN-08), cancelacion, agenda del propietario (horarios/bloqueos,
  RN-03), atencion de reservas recibidas, pago simulado + reembolso (RN-05),
  liquidaciones/comisiones (RN-07), expiracion del hold + revocacion (L-14), cierre automatico a
  `FINALIZADA` (L-15), calificaciones (RN-04), reclamos + avisos in-app + moderacion (F06/CU06.6),
  administracion completa F07, promociones aplicadas (CU04.6), catalogo avanzado (F02/F03),
  edicion/baja logica de establecimiento y cancha (CU04.1/CU04.2), comparacion (CU02.2),
  revocacion de access tokens (L-20) y rate limiting de login (S-4). **Todos los CU del backend
  implementados**; F05 real (pasarela/desembolso) queda **fuera de alcance por decision del
  usuario** y documentado.
- **Evidencia vigente (2026-10-17):** `mvnw test` -> **144/144 PASS** (`BUILD SUCCESS`); humo
  -> **112/112 PASS** en `smoke.ps1` y `smoke.sh` contra el contenedor (8089/3309, imagen
  reconstruida). CI en `.github/workflows/backend.yml` (MySQL 8.4 efimero).
- **Estado:** Fase 4 completada y cerrada. Exclusiones documentadas: F05 real, penalidad
  parcial/no-show (L-12) y excepciones de agenda por fecha (L-13).
- **Frontend (2026-10-17):** Angular 22.2.3 standalone + CDK. **Rediseño de fusión aplicado**
  (ver `docs/DISENO_FUSION.md`): identidad bosque + lima, tipografías locales Outfit/DM Sans,
  header/footer nuevos, home editorial, catálogo con filtros laterales, detalle con galería
  carrusel y widget de reserva sticky, login a dos columnas. Se conservan carrusel de fotos,
  calendario semanal, selección día/hora con precios y comparación. Tokens/paletas, shell responsive,
  auth completa, catalogo/detalle/disponibilidad/comparacion, reserva + pago demo, mis reservas
  con cancelacion/calificacion, perfil/solicitud de propietario, notificaciones, reclamos,
  panel del propietario (establecimiento con edicion y baja, canchas con edicion y fotos, cocheras,
  reservas recibidas, agenda, promociones, liquidaciones) y administracion (propietarios con
  asignacion automatica de OWNER, reclamos, usuarios/roles en modal, promociones, cocheras,
  supervision, auditoria). **Todas las listas extensas usan un paginador reutilizable** (10 por
  pagina; catalogo 12) con ancho fijo para evitar saltos. El panel **descubre los establecimientos
  con `GET /establecimientos/mios`** (sin depender del ID manual ni de localStorage), permite cambiar entre varios
  y la vista publica se abre en la misma pestaña, con boton “Volver a configurar” y galeria
  completa (todas las miniaturas, visor y fallback para URLs rotas). El catalogo muestra
  **distrito, foto, reputacion con numero de opiniones, servicios y favoritos persistidos**
  (`GET/POST/DELETE /favoritos`) y una pagina `/favoritos`; comparar es un boton por tarjeta.
  Los paneles de cliente, propietario y administración usan **sidebar lateral**. El registro **auto-inicia sesion y vuelve a la ruta
  original** (reserva/mis reservas). Un `PLATFORM_ADMIN` sin rol `OWNER` ya no ve el flujo de
  solicitud: se le indica que asigne `OWNER` desde Usuarios → Roles o use Supervision. El perfil
  permite **reenviar la verificacion de correo**.
  `npm run build` **OK**, `npm test` **15/15 PASS** y E2E Playwright **OK** en dev y en Docker
  (`http://localhost:8083`). Incluye paginación numérica del catálogo (`X-Total-Count`, 12 por
  página), **detalle rediseñado con galería, bloques de información y reserva sticky**, **calendario
  semanal hora × día con navegación de semanas, salto de mes y ventana de 365 días**, textos con
  tildes/ñ y sin referencias a proyecto universitario. Corregido el `disabled` de los botones
  (afectaba Reservar y paginación). Node 22.13 del host no basta: se usó Node 24 portable;
  Dockerfile usa `node:24-alpine`.
- **Cambio de backend para Fase 4:** `GET /api/v1/canchas` acepta `idEstablecimiento`,
  `desplazamiento` y devuelve `X-Total-Count` (paginación); necesario para listar canchas propias
  fuera del límite del catálogo. La ventana de anticipación sube de 7 a **365 días**
  (`cd.reservas.dias-anticipacion-max`). Imagen backend reconstruida, suite **144/144** y humo
  **112/112**.
- **Ultima fecha de trabajo:** 2026-10-17
- **Rama/commit local:** sin repositorio Git inicializado (carpeta de trabajo).
- **No afirmar** que el producto esta terminado: los criterios de §14 del prompt
  maestro **no** se cumplen todavia.

## 1. Objetivo

Construir CanchaDirecta completo (backend Quarkus Java 21 hexagonal + frontend
Angular responsive + MySQL del host con Flyway + Docker), cubriendo la union de
requisitos de `Avance_2_Desarrollo_Web_Integrado.pdf`, `Sistema Web Integrado.pptx`
y `CanchaDirecta_Entrega_02_CORREGIDA_FINAL(1).zip`. Ver
`docs/REQUISITOS_Y_RECONCILIACION.md` y `docs/TRAZABILIDAD.md`.

## 2. Fuentes

- `../instrucciones/CANCHADIRECTA_PROMPT_MAESTRO_AGENTE.md` (mandato; 627 lineas).
- `../instrucciones/CANCHADIRECTA_ANALISIS_TECNICO.md` (reconciliacion/riesgos).
- `../instrucciones/CANCHADIRECTA_HANDOFF_INICIAL.md`.
- `../fuentes/Avance_2_Desarrollo_Web_Integrado.pdf`, `../fuentes/Sistema Web Integrado.pptx`.
- `../fuentes/CanchaDirecta_Entrega_02_CORREGIDA_FINAL(1).zip` (extraido en
  `../_fuentes_extraidas/`): DBML, diccionario XLSX, SQL Server fisico, 9 mockups,
  6 casos de uso, BPMN, diagramas, informes de validacion.

## 3. Estructura

```text
canchadirecta/
  README.md  HANDOFF.md  .env.example  .gitignore
  compose.yml  compose.mysql.yml
  backend/                                (Quarkus: compila, migra y sirve la API)
    pom.xml  mvnw  mvnw.cmd  .mvn/
    src/main/java/pe/com/canchadirecta/
      shared/     (dominio base, errores, reloj, recursos de sistema)
      identity/   (usuarios, sesiones, refresh, recuperacion de clave)
      venues/     (establecimientos, canchas, catalogo (distritos/deportes/ficha), horarios y bloqueos)
      bookings/   (reservas con SELECT ... FOR UPDATE, expiracion y cierre)
      payments/   (pago simulado + reembolso, pasarela por puerto)
      settlements/(liquidaciones y comisiones del propietario, RN-07)
      engagement/ (calificaciones, reclamos, avisos in-app y su moderacion; CU03.8/CU02.5/CU06.6, RN-04)
      platform/   (usuarios/roles/estado, validacion de propietarios, supervision, auditoria, cocheras y promociones; CU06.1..CU06.7)
    src/main/resources/application.properties
    src/main/resources/db/migration/V1..V6
    src/test/java/pe/com/canchadirecta/     (esquema, identity, venues, bookings, payments, settlements, engagement, platform)
  docs/                                   (requisitos, arquitectura, pruebas, seguridad)
  scripts/                                (bootstrap de BD, clave JWT, humo PS/Bash)
  frontend/                               (Angular 22 standalone; ver frontend/README.md)
    src/app/core/       tokens, auth (store/guards/interceptors), clientes API por modulo
    src/app/shared/     shell, UI propia (CDK), toast, confirm, estados
    src/app/features/   auth, public, catalogo, reservas, cuenta, panel, admin
    src/styles/         tokens/paletas, base, utilidades, componentes
```

## 4. Modo de ejecucion local (Modo A)

- MySQL del host: `127.0.0.1:3306`, base `canchadirecta` (utf8mb4), ya creada.
- Usuarios: `canchadirecta_mig` (DDL, Flyway) y `canchadirecta_app` (DML minimo).
  Contrasenas en `canchadirecta/.env` (gitignored).
- Backend: `JAVA_HOME=B:\cursos\java\jdk-21.0.2`, `./mvnw quarkus:dev` -> puerto 8088
  (el 8080 esta ocupado por otro servicio del equipo).
- Comando de pruebas: `./mvnw test` (arranca Quarkus -> Flyway migra -> test consulta
  `information_schema`).

## 5. COMPLETADO (con evidencia)

| # | Elemento | Evidencia |
|---|---|---|
| 1 | Reconocimiento de entorno (OS, JDK, Maven, Node, MySQL, Docker, puertos) | comandos ejecutados; resumen en README ?Requisitos |
| 2 | Fuentes extraidas y leidas (prompt, analisis, DBML, SQL Server fisico) | `_fuentes_extraidas/` |
| 3 | Base `canchadirecta` + usuarios minimos creados | `mysql ... SHOW SCHEMAS` -> `canchadirecta` utf8mb4 |
| 4 | Script de bootstrap seguro e idempotente | `scripts/db-bootstrap-safe.ps1` (ejecutado OK) |
| 5 | Migraciones Flyway V1-V4 (28 tablas + geografia + extensiones + catalogos) | `flyway_schema_history` 4 filas success |
| 6 | Backend Quarkus 3.40.1 compila y arranca; test de esquema PASS | `mvnw test` -> BUILD SUCCESS, 38 tablas |
| 7 | **Slice `identity` - registro** (`POST /api/v1/auth/register`) hexagonal: dominio puro + puertos + servicio + adaptadores (JPA, bcrypt) | `RegistroUsuarioTest` 4/4 PASS |
| 8 | **Slice `identity` - login JWT + sesion** (`POST /api/v1/auth/login`, `GET /api/v1/users/me`) con refresh opaco hasheado en `sesion_refresh` | `AutenticacionTest` 5/5 PASS |
| 9 | Clave de firma JWT en JWK local (`.secrets/`, gitignored) generada por script | `scripts/jwt-key-bootstrap.ps1` (ejecutado OK) |
| 10 | **Slice `identity` - refresh con rotacion + logout** (`POST /api/v1/auth/refresh`, `POST /api/v1/auth/logout`), deteccion de reutilizacion -> revoca todas las sesiones | `SesionRefreshTest` 5/5 PASS |
| 11 | Compose: puertos libres (3309/8089/8083), clave JWT montada de solo lectura en el contenedor | `docker compose config` OK |
| 12 | Scripts de humo (PowerShell + Bash) recorriendo el flujo real por HTTP | `smoke.ps1` -> **10/10 PASS** |
| 13 | **Stack Docker completo verificado** (Modo C: MySQL 8.4 + backend) | `compose up` OK; 38 tablas; `smoke.ps1` en 8089 -> **10/10 PASS** |
| 14 | **Recuperacion de contrasena** (`/auth/password-reset/request` + `/confirm`), token de un solo uso en `auth_token`, sin enumeracion de cuentas | `RecuperacionPasswordTest` 5/5 PASS |
| 15 | **Swagger UI publicado tambien en `prod`** (antes 404 en el contenedor) | `/q/swagger-ui` -> 200; `/q/openapi` con las 8 rutas |
| 16 | **Slice `venues`** - alta de establecimiento (el usuario queda como propietario) y de cancha, mas listado publico filtrable por distrito | `ReservasTest` (setup) + `smoke.ps1` pasos 13-15 |
| 17 | **Slice `bookings` - reserva con `SELECT ... FOR UPDATE`** sobre la fila de `cancha`, comprobacion de solape y bloqueos bajo el mismo lock, `historial_reserva`, `409 SLOT_UNAVAILABLE` | `ReservasTest` **7/7 PASS** |
| 18 | **RN-01 verificado con 30 peticiones HTTP simultaneas** -> exactamente 1 reserva (201), 29 conflictos (409) y 1 fila en BD | 3 repeticiones consecutivas: **3/3 PASS** |
| 19 | **Disponibilidad por fecha** (`GET /api/v1/canchas/{id}/disponibilidad`): bloques alineados a la ventana de atencion (o la ventana por defecto 06:00-23:00), descontando reservas vigentes y bloqueos; publico | `ReservasTest` (3 tests) + humo |
| 20 | **Cancelacion de reserva propia** (`POST /api/v1/reservas/{id}/cancelar`): ventana minima configurable, traza en `historial_reserva`, liberacion inmediata de la franja y `409` en el segundo intento | `ReservasTest` (4 tests) + humo |
| 21 | **Agenda del propietario - horarios** (`GET/POST /api/v1/canchas/{id}/horarios`, `DELETE .../horarios/{id}`): franjas semanales con precio opcional, sin solapes, baja logica; la disponibilidad usa la agenda cuando existe y ya no la ventana por defecto | `AgendaTest` (4 tests) + humo |
| 22 | **Bloqueos de cancha (RN-03)** (`GET/POST /api/v1/canchas/{id}/bloqueos`, `DELETE .../bloqueos/{id}`): alta bajo el mismo lock de cancha que RN-01, `409` si choca con una reserva vigente o con otro bloqueo, la franja deja de ofrecerse y no se puede reservar | `AgendaTest` + humo |
| 23 | **Bandeja del propietario** (`GET /api/v1/reservas/recibidas`): reservas de sus canchas (`reserva -> cancha -> establecimiento -> propietario`), con filtro opcional de estado y `403` para quien no es propietario | `ReservasTest` (3 tests) + humo |
| 24 | **Confirmacion y rechazo de reservas (HU-008-2)** (`POST /api/v1/reservas/{id}/confirmar`, `POST .../{id}/rechazar`): `FOR UPDATE` de la reserva, traza en `historial_reserva`, estado `RECHAZADA` (V5, ADR-0018) y liberacion inmediata de la franja | `ReservasTest` (6 tests nuevos) + humo |
| 25 | **Cochera opcional (RN-08)** (`GET/POST /api/v1/establecimientos/{id}/cocheras`, `DELETE .../{idCochera}`, `GET /api/v1/canchas/{id}/cocheras?inicio=&fin=` y `cocheraId` opcional en `POST /api/v1/reservas`): capacidad por cochera, cupo por solape de reservas vigentes, coherencia de establecimiento, `montoCochera = tarifa_hora x horas` y liberacion al cancelar/rechazar (ADR-0019) | `CocheraTest` (12 tests) + humo (47/47) |
| 26 | **Pago simulado + reembolso (RN-05, L-12)** (modulo `payments`; `POST/GET /api/v1/reservas/{idReserva}/pago`): simulador determinista tras `PaymentGatewayPort`, importe `monto_cancha - monto_descuento + monto_cochera`, **RN-05** (un solo pago aprobado por reserva), y **reembolso** total por rechazo / ventana de 24 h por cancelacion (ADR-0020) | `PagoTest` (11 tests) + humo (53/53) |
| 27 | **Liquidaciones y comisiones del propietario (F05, RN-07)** (modulo `settlements`; `POST /api/v1/liquidaciones`, `GET /api/v1/liquidaciones`, `GET /api/v1/liquidaciones/{id}`): liquida las reservas del propietario con pago `APROBADO`, estado `CONFIRMADA`/`FINALIZADA`, `DATE(inicio)` dentro del periodo y aun no liquidadas; comision configurable `cd.liquidaciones.porcentaje-comision` (10 %), por linea `comision = round(monto*pct/100,2)` y `neto = monto - comision`; **RN-07** (una reserva en maximo una liquidacion) con `SELECT ... FOR UPDATE` por propietario + `NOT EXISTS` y `UNIQUE(id_reserva)` como garantia final (ADR-0021) | `LiquidacionTest` (10 tests) + humo (59/59) |
| 28 | **Expiracion del hold de pago + revocacion (L-14)** (modulo `bookings`): `@Scheduled(60s)` sobre `quarkus-scheduler` expira a `EXPIRADA` (V6) las reservas `PENDIENTE` **sin pago `APROBADO`** cuyo hold (`config_establecimiento.hold_pago_min`, o `cd.reservas.hold-pago-min` = 10) vencio, medido contra `NOW(6)` (reloj de la base) y con `FOR UPDATE OF r` (no bloquea la cancha); y `POST /api/v1/reservas/{id}/revocar` permite al propietario revocar una reserva `CONFIRMADA` no comenzada -> `RECHAZADA` con **reembolso total** (ADR-0022) | `ExpiracionReservasTest` (4 tests) + `ReservasTest` (23, +3 de revocacion) + humo (63/63) |
| 29 | **Cierre automatico de reservas concluidas (L-15)** (modulo `bookings`): `@Scheduled(60s)` cierra a `FINALIZADA` (estado ya existente, sin migracion) las reservas `CONFIRMADA` cuyo `fin` ya quedo en el pasado, medido contra `UTC_TIMESTAMP(6)` (las franjas son literales UTC, ADR-0014) y con `FOR UPDATE` (solo la reserva); sin reembolso ni cambio de importe; el lote se acota con `cd.reservas.finalizacion-lote-max` (200) (ADR-0023) | `FinalizacionReservasTest` (4 tests) |
| 30 | **Calificaciones de reservas finalizadas (F06, CU03.8/CU02.5, RN-04)** (modulo `engagement`): `POST /api/v1/reservas/{idReserva}/calificacion` (solo el titular del `sub` del JWT, solo reserva `FINALIZADA`, puntaje 1..5 y comentario <= 500, `409` si ya existe) y `GET /api/v1/canchas/{idCancha}/calificaciones?limite=` (publico: total, promedio y mas recientes con `JOIN reserva`). Sin migracion: usa la tabla `calificacion` de V1 y su `UNIQUE(id_reserva)` como garantia final de RN-04; `fecha_registro` se escribe como literal UTC (ADR-0014) | `CalificacionTest` (7 tests) + humo (69/69) |
| 31 | **Reclamos de soporte + avisos in-app (F06, CU06.6)** (modulo `engagement`): `POST /api/v1/reclamos` (reserva opcional propia), `GET /api/v1/reclamos/mios`, `GET /api/v1/reclamos/{id}` (con seguimientos), `POST /api/v1/reclamos/{id}/seguimientos` (`409` si esta cerrado); `GET /api/v1/notificaciones` (`noLeidas`/`total`), `POST /api/v1/notificaciones/{id}/leida`. Sin migracion: usa `reclamo`, `reclamo_seguimiento` (V1/V3) y `notificacion` (V1). Los avisos se emiten desde reservas/pagos por el puerto compartido `AvisosUsuario` (confirmar, rechazar, revocar, finalizar, pago aprobado), con `fecha` UTC explicita (ADR-0014) | `ReclamoNotificacionTest` (8 tests) + humo (75/75) |
| 32 | **Moderacion de reclamos (F07, CU06.6)** (modulo `engagement`): `/api/v1/admin/reclamos` con `@RolesAllowed("PLATFORM_ADMIN")` (el rol viaja en `groups` del JWT): listado con filtro por estado, detalle con conversacion, `POST .../{id}/estado` (transiciones explicitas, cambio condicional al estado leido -> `409` si otra moderacion gano) y `POST .../{id}/seguimientos` (respuesta oficial). Cada accion avisa al titular por `AvisosUsuario`. Sin migracion | `ModeracionReclamoTest` (5 tests) + humo (81/81) |
| 33 | **Validacion de propietarios + supervision + auditoria (F07, CU06.2/CU06.3/CU06.4/CU06.7)** (modulo `platform`, `PLATFORM_ADMIN`): `GET /api/v1/admin/propietarios` y `POST .../{id}/validacion` (transiciones `PENDIENTE->VALIDADO|RECHAZADO`, `RECHAZADO->VALIDADO`, `VALIDADO->RECHAZADO`; motivo obligatorio al rechazar; `409` condicional; aviso al propietario), `GET /api/v1/admin/establecimientos|reservas|pagos` (solo lectura) y `GET /api/v1/admin/auditoria`. Puerto compartido `RegistroAuditoria` sobre la tabla `auditoria` (fecha UTC) que tambien audita la moderacion de reclamos. Sin migracion | `AdminPlataformaTest` (4 tests) + humo (87/87) |
| 34 | **Usuarios, roles y estado de cuenta (F07, CU06.1)** (modulo `platform`): `GET /api/v1/admin/usuarios?correo=&estado=&rol=` (roles via `GROUP_CONCAT`), `GET /{id}`, `POST /{id}/roles` (reemplazo completo, `400` si vacio/desconocido, `409` si dejaria la plataforma sin admin activo) y `POST /{id}/estado` (`ACTIVO/INACTIVO/BLOQUEADO`, condicional, login ya rechaza cuentas no activas). Audita `USUARIO_ROLES_CAMBIADOS`/`USUARIO_ESTADO_CAMBIADO` y avisa al afectado. Los tokens vigentes no se revocan (L-20). Sin migracion | `AdminUsuariosTest` (4 tests) + humo (91/91) |
| 35 | **Cocheras y promociones (F07, CU06.5)** (modulo `platform`): `GET/POST /api/v1/admin/promociones` (codigo unico 1..30 `[A-Z0-9_-]`, `PORCENTAJE`/`MONTO`, valor > 0 y <= 100 si es porcentaje, `inicio <= fin`, canchas opcionales que deben existir) y `POST /{id}/estado`; `GET /api/v1/admin/cocheras` y `POST /{id}/estado`. Cambios condicionales (`409`) y auditoria `PROMOCION_*`/`COCHERA_*`. Sin migracion (tablas V1); la aplicacion al precio llega en #36 | `AdminCocherasPromocionesTest` (4 tests) + humo (96/96) |
| 36 | **Promociones del propietario aplicadas a la reserva (CU04.6)** (modulo `platform` para el catalogo, `bookings` para el precio): `GET/POST /api/v1/establecimientos/{id}/promociones` y `POST /{id}/estado` (ownership por `propietario.id_usuario`, el alta asocia todas las canchas); `POST /api/v1/reservas` acepta `codigoPromocion` y el puerto compartido `PromocionesCancha` resuelve la promocion activa **para la fecha de la reserva**. `PORCENTAJE` -> `precio*valor/100`, `MONTO` -> fijo con tope al precio, guardado en `monto_descuento`; pago y liquidacion ya lo descuentan. Sin migracion | `PromocionReservaTest` (4 tests) + humo (98/98) |
| 37 | **Catalogo avanzado (F02/F03, ADR-0031)**: `GET /api/v1/ubicaciones/distritos` (provincia/departamento, `q`, resuelve L-10), `GET /api/v1/deportes`, `GET /api/v1/caracteristicas`; `GET /api/v1/canchas` con `idDistrito`, `idDeporte`, `precioMax` y `q`; `GET /api/v1/canchas/{id}` (ficha con establecimiento, distrito, deportes, caracteristicas, fotos y reputacion); `PUT /canchas/{id}/deportes|caracteristicas` y `POST/DELETE /canchas/{id}/fotos` con ownership. Sin migracion (tablas V1/V2/V4) | `CatalogoCanchaTest` (4 tests) + humo (104/104) |
| 38 | **Edicion (CU04.1), baja logica (CU04.2) y comparacion (CU02.2, ADR-0032)**: `PUT /api/v1/establecimientos/{id}` y `PUT /api/v1/canchas/{id}` con ownership (`404` inexistente, `403` ajeno, `409` nombre repetido, `400` validacion); `DELETE` de ambos con baja logica (`INACTIVO`/`INACTIVA`, el establecimiento arrastra sus canchas) y `409` si hay reservas `PENDIENTE`/`CONFIRMADA` futuras; el listado publico oculta lo inactivo y la disponibilidad/reserva ya lo rechazan; `GET /api/v1/canchas/comparar?ids=` devuelve 2..4 fichas en orden (`400`/`404`). Sin migracion (`estado` existe desde V1) | `EdicionYBajaVenuesTest` (6 tests) + humo (112/112) |
| 39 | **Identidad completa (CU01.2/CU01.5/CU01.6, ADR-0033)**: verificacion de correo (`usuario.correo_verificado`, `POST /auth/email/verify`, `POST /auth/email/resend`, flag `cd.auth.requerir-correo-verificado`); perfil editable (`PUT /users/me`); solicitud de propietario (`POST /propietarios/solicitudes`, `GET /propietarios/me`, `409` documento ajeno). Migracion V7 | `PerfilVerificacionPropietarioTest` (4 tests) |
| 40 | **Reservas: idempotencia y reprogramacion (CU03.2/CU03.7, ADR-0034)**: `Idempotency-Key` persistida con `UNIQUE(id_usuario, clave_idempotencia)` y replay con el mismo `id`; `POST /reservas/{id}/modificar` reprograma reservas propias vigentes conservando duracion e importes, con revalidacion de solape/bloqueo/cochera y locking cancha -> cochera -> reserva. Migracion V8 | `IdempotenciaModificacionTest` (2 tests) |
| 41 | **Endurecimiento (S-4/L-20/mapper 409, ADR-0033)**: limite de 5 fallos de login por correo con bloqueo de 5 min (`429 TOO_MANY_ATTEMPTS`); revocacion de access tokens por `usuario.token_version` (claim `ver` + filtro posterior a la autenticacion) al cambiar roles, estado o contrasena; violacion de `uq_calificacion_reserva` traducida a `409`. Migraciones V9/V10 | `EndurecimientoAccesoTest` (3 tests) + `AdminUsuariosTest` |

**Evidencia de pruebas (2026-10-17):** `Tests run: 144, Failures: 0, Errors: 0` - `BUILD SUCCESS`
- Identidad completa (CU01.2/CU01.5/CU01.6): 4 tests (`PerfilVerificacionPropietarioTest`).
- Idempotencia y reprogramacion (CU03.2/CU03.7): 2 tests (`IdempotenciaModificacionTest`).
- Endurecimiento (S-4/L-20): 3 tests (`EndurecimientoAccesoTest`) + `AdminUsuariosTest` ajustado.
- Edicion, baja y comparacion de canchas (CU02.2/CU04.1/CU04.2): 6 tests de
  `EdicionYBajaVenuesTest` (`PUT` con ownership y validaciones, baja logica con `409` por reservas
  vigentes, cascada del establecimiento y comparacion de 2..4 fichas).
- Catalogo avanzado (F02/F03): 4 tests de `CatalogoCanchaTest` (catalogos, ficha con atributos y
  fotos, filtros, `403` ajeno y validaciones).
- Promociones aplicadas (CU04.6): 4 tests de `PromocionReservaTest` (ownership, 10% -> descuento
  8.00 y pago 72.00, tope al precio, `409` por codigo invalido; vigencia por fecha de reserva).
- Cocheras y promociones (F07, CU06.5): 4 tests de `AdminCocherasPromocionesTest` (`403` a `USER`,
  alta/listado/baja de promocion con validaciones y auditoria, supervision y baja de cochera).
- Gestion de usuarios y roles (F07, CU06.1): 4 tests de `AdminUsuariosTest` (`403` a `USER`,
  filtros/detalle, cambio de roles con aviso y auditoria, desactivacion con login `403`).
- Administracion de plataforma (F07): 4 tests de `AdminPlataformaTest` (`403` a `USER`,
  validacion con aviso y auditoria, rechazo con motivo y revalidacion, supervision de
  establecimientos/reservas/pagos).
- Moderacion de reclamos (F07, CU06.6): 5 tests de `ModeracionReclamoTest` (`403` a `USER`,
  listado/filtros, cambio de estado con aviso, transiciones invalidas `409`, respuesta oficial).
- Reclamos y avisos (F06, CU06.6): 8 tests de `ReclamoNotificacionTest` (alta/bangeja/detalle con
  seguimiento, `403` ajeno, `409` cerrado, `400` de validacion, aviso de confirmacion y marcado
  como leido con `404` ajeno).
- Calificaciones (F06, RN-04): 7 tests de `CalificacionTest` (alta y listado publico con promedio,
  reserva no finalizada `409`, doble calificacion `409`, reserva ajena `403`, puntaje fuera de rango
  `400`, reserva/cancha inexistente `404`, promedio de varias calificaciones).
- Cochera (RN-08): 12 tests de `CocheraTest`; concurrencia 4 canchas -> exactamente 2, **3/3 PASS**.
- Pago/reembolso (RN-05, L-12): 11 tests de `PagoTest` (simulador determinista, RN-05, importe con
  cochera, reembolso total/ventana e idempotencia).
- Liquidaciones/comisiones (F05, RN-07): 10 tests de `LiquidacionTest` (calculo 80.00 -> comision
  8.00 / neto 72.00, periodo, exclusion de no liquidadas y RN-07).
- Expiracion + revocacion (ADR-0022, L-14): 4 tests de `ExpiracionReservasTest` + 3 nuevos de
  `ReservasTest`.
- Cierre automatico de reservas concluidas (ADR-0023, L-15): 4 tests de `FinalizacionReservasTest`
  (finaliza confirmada vencida y traza, no finaliza vigente, no finaliza pendiente, no arrastra
  vigentes del lote).
- `smoke.ps1` -> **112/112 PASS** (contenedor en 8089 con la imagen reconstruida y `-DbPort 3309`;
  8 pasos nuevos de edicion, baja y comparacion).
- `smoke.sh` -> **112/112 PASS** con Git Bash (`DB_PORT=3309 bash scripts/smoke.sh http://localhost:8089`).
  Los pasos de calificacion y administracion necesitan el cliente `mysql`; si falta, se omiten con
  `[SKIP]` (el resto del humo sigue siendo HTTP).

## 6. PENDIENTE (ordenado por riesgo/bloqueo)

1. **Backend por casos de uso** (Fase 3): **completado**. Identidad, venues + reservas,
   disponibilidad, agenda, atencion, cochera, pagos/reembolsos, liquidaciones, hold +
   revocacion, cierre automatico, calificaciones, reclamos/avisos/moderacion, administracion F07,
   promociones aplicadas, catalogo avanzado, edicion/baja de establecimiento y cancha
   (CU04.1/CU04.2) y comparacion de canchas (CU02.2).
2. **Concurrencia de reservas (RN-01)**: **HECHO Y VERIFICADO** (30 hilos -> 1 reserva).
   Bloqueos de cancha (RN-03) y cochera (RN-08): **HECHO** bajo el mismo lock
   (orden cancha -> cochera, ADR-0019). Pago aprobado unico por reserva (RN-05): **HECHO**
   (indice unico `uq_pago_aprobado_reserva`).
3. **Seguridad**: JWT/refresh, ownership y `@RolesAllowed("PLATFORM_ADMIN")` hechos (el rol viaja
   en `groups`); limitación de intentos (`S-4`), revocación de tokens (`L-20`) y CORS por entorno:
   **hechos y verificados**. Mantener revisión al cambiar configuración.
4. **Frontend Angular 22** (Fase 4): operativo y verificado con E2E en dev y Docker. Detalle
   rediseñado, calendario semanal, edicion/baja de establecimiento y reenvio de verificacion
   listos. Pendiente de pulido: revision manual de teclado/contraste. Ver
   `docs/LIMITACIONES_CONOCIDAS.md` (L-23).
5. **Docker**: `compose.yml`, `compose.mysql.yml` y Dockerfiles de backend **y frontend**
   verificados (`frontend` en 8083 con proxy `/api`).
6. **Docs**: base completa (requisitos, arquitectura, modelo, seguridad, pruebas, limitaciones,
   ADR-0001..**0036**, contratos API del frontend). Faltan diagramas finales y limpiar filas
   obsoletas de `TRAZABILIDAD.md`; evidencia E2E ya existe.
7. **Testcontainers**: pruebas de integracion con MySQL real en CI (Docker requerido).

## 7. DECISIONES / ADRs

ADR-0001 monolito modular hexagonal - ADR-0002 versiones fijadas (Quarkus 3.40.1,
Java 21, Angular 22) - ADR-0003 jerarquia geografica + ubigeo - ADR-0004
`tipo_espacio`/`estado_reserva` como catalogo/enum controlado - ADR-0005 concurrencia
por `SELECT ... FOR UPDATE` (sin triggers) - ADR-0006 unicidad parcial con columna
generada - ADR-0007 extensiones minimas justificadas - ADR-0008 zona horaria -
ADR-0009 credenciales de BD separadas - ADR-0010 puertos - ADR-0011 JWT con clave local -
ADR-0012 SQL nativo selectivo - ADR-0013 lock de reservas - ADR-0014 `DATETIME` como UTC
explicito - ADR-0015 disponibilidad por fecha - ADR-0016 cancelacion con ventana minima -
ADR-0017 agenda del propietario - ADR-0018 atencion de reservas (`RECHAZADA`) -
ADR-0019 cochera opcional con orden de locks **cancha -> cochera** - ADR-0020 pago simulado
+ reembolso (frontera `PaymentGatewayPort`, RN-05 y politica de reembolso por ventana) -
ADR-0021 modulo `settlements` (liquidaciones/comisiones del propietario, RN-07: una reserva en
maximo una liquidacion, con lock por propietario y `UNIQUE(id_reserva)`) - ADR-0022 expiracion del
hold de pago (`EXPIRADA`, V6, job `@Scheduled` con `quarkus-scheduler`, medida contra `NOW(6)`) y
revocacion de reservas confirmadas (`L-14`: `CONFIRMADA` -> `RECHAZADA` con reembolso total) -
ADR-0023 cierre automatico de reservas concluidas (`FINALIZADA`, job `@Scheduled`, medido contra
`UTC_TIMESTAMP(6)` porque las franjas son literales UTC; sin reembolso) - ADR-0024 calificaciones de
reservas finalizadas (modulo `engagement`, sin migracion: tabla `calificacion` de V1 con
`UNIQUE(id_reserva)`, solo el titular y solo `FINALIZADA`, listado publico con promedio via
`JOIN reserva`, `fecha_registro` en UTC explicito) - ADR-0025 reclamos de soporte y avisos in-app
(`reclamo`/`reclamo_seguimiento`/`notificacion` de V1/V3, reclamo propio con seguimiento, estados
solo por admin, puerto compartido `AvisosUsuario` para emitir avisos desde reservas y pagos) -
ADR-0026 moderacion de reclamos por `PLATFORM_ADMIN` (`@RolesAllowed`, transiciones explicitas,
cambio condicional `409` y avisos al titular) - ADR-0027 administracion de plataforma (modulo
`platform`: validacion de propietarios, supervision de solo lectura y auditoria via el puerto
compartido `RegistroAuditoria`) - ADR-0028 gestion de usuarios, roles y estado de cuenta
(listado/filtros, reemplazo de roles, `409` si dejaria la plataforma sin administradores activos,
auditoria y avisos; los tokens vigentes no se revocan, L-20) - ADR-0029 administracion de cocheras
y promociones (estado de cocheras y catalogo de promociones con `cancha_promocion`, validaciones,
baja condicional y auditoria) - ADR-0030 promociones del propietario aplicadas a la reserva (alta
por establecimiento, `codigoPromocion` en la reserva, vigencia por fecha de reserva, descuento
porcentual/fijo con tope y `monto_descuento`; pago y liquidacion lo descuentan; L-21 resuelta) -
ADR-0031 catalogo avanzado (distritos/deportes/caracteristicas, filtros, ficha con fotos y
reputacion, gestion de atributos por el propietario; L-10 resuelta) - ADR-0032 edicion y baja
logica de establecimientos y canchas (ownership, `409` con reservas vigentes, filtrado publico de
lo inactivo) y comparacion de canchas (`/canchas/comparar`, 2..4 fichas) - ADR-0033 identidad
completa (verificacion de correo, perfil, solicitud de propietario, rate limiting de login y
revocacion de access tokens por version) - ADR-0034 idempotencia de reservas y reprogramacion con
duracion fija.
Ver `docs/DECISIONES_ARQUITECTURA.md`.

## 8. PRUEBAS EJECUTADAS

| Fecha | Comando | Resultado |
|---|---|---|
| 2026-10-16 | `backend/mvnw.cmd -B test` | **PASS** (20 tests: esquema, registro, login JWT, refresh/logout, recuperacion de clave) |
| 2026-10-16 | `scripts/smoke.ps1` (8088 y 8089) | **PASS** (12/12 pasos) |
| 2026-10-16 | `scripts/db-bootstrap-safe.ps1` | **PASS** (base y usuarios creados, sin tocar otras bases) |
| 2026-10-16 | `scripts/jwt-key-bootstrap.ps1` | **PASS** (clave JWK creada en `.secrets/`, no versionada) |
| 2026-10-16 | `backend/mvnw.cmd -B test` | **PASS** (27 tests: + venues/reservas y RN-01 con 30 hilos) |
| 2026-10-16 | `scripts/smoke.ps1 -BaseUrl http://localhost:8089` | **PASS** (19/19 pasos, contenedor con la imagen reconstruida) |
| 2026-10-16 | `backend/mvnw.cmd -B test` | **PASS** (34 tests: + disponibilidad y cancelacion) |
| 2026-10-16 | `bash scripts/smoke.sh http://localhost:8089` | **PASS** (24/24 pasos, Git Bash; se corrigio la deteccion de `python3`) |
| 2026-10-17 | `backend/mvnw.cmd -B test` | **PASS** (39 tests: + agenda del propietario y RN-03) |
| 2026-10-17 | `scripts/smoke.ps1 -BaseUrl http://localhost:8089` | **PASS** (33/33 pasos, contenedor con la imagen reconstruida) |
| 2026-10-17 | `bash scripts/smoke.sh http://localhost:8089` | **PASS** (33/33 pasos, Git Bash) |
| 2026-10-17 | `backend/mvnw.cmd -B test` | **PASS** (45 tests: + atencion de reservas por el propietario y `V5`) |
| 2026-10-17 | `scripts/smoke.ps1 -BaseUrl http://localhost:8089` | **PASS** (39/39 pasos, contenedor con la imagen reconstruida) |
| 2026-10-17 | `bash scripts/smoke.sh http://localhost:8089` | **PASS** (39/39 pasos, Git Bash) |
| 2026-10-17 | `backend/mvnw.cmd -B test` | **PASS** (57 tests: + cochera opcional RN-08 y `ADR-0019`) |
| 2026-10-17 | `scripts/smoke.ps1 -BaseUrl http://localhost:8089` | **PASS** (47/47 pasos, + 8 de cochera; contenedor con la imagen reconstruida) |
| 2026-10-17 | `bash scripts/smoke.sh http://localhost:8089` | **PASS** (47/47 pasos, Git Bash) |
| 2026-10-17 | `backend/mvnw.cmd -B test` | **PASS** (68 tests: + pago simulado y reembolso, RN-05, `ADR-0020`) |
| 2026-10-17 | `scripts/smoke.ps1 -BaseUrl http://localhost:8089` | **PASS** (53/53 pasos, + 6 de pago/reembolso; contenedor con la imagen reconstruida) |
| 2026-10-17 | `bash scripts/smoke.sh http://localhost:8089` | **PASS** (53/53 pasos, Git Bash) |
| 2026-10-17 | `backend/mvnw.cmd -B test` | **PASS** (78 tests: + liquidaciones/comisiones, RN-07, `ADR-0021`) |
| 2026-10-17 | `scripts/smoke.ps1 -BaseUrl http://localhost:8089` | **PASS** (59/59 pasos, + 6 de liquidaciones; contenedor con la imagen reconstruida) |
| 2026-10-17 | `bash scripts/smoke.sh http://localhost:8089` | **PASS** (59/59 pasos, Git Bash) |
| 2026-10-17 | `backend/mvnw.cmd -B test` | **PASS** (85 tests: + expiracion del hold y revocacion, `ADR-0022`) |
| 2026-10-17 | `scripts/smoke.ps1 -BaseUrl http://localhost:8089` | **PASS** (63/63 pasos, + 4 de revocacion; contenedor con la imagen reconstruida) |
| 2026-10-17 | `bash scripts/smoke.sh http://localhost:8089` | **PASS** (63/63 pasos, Git Bash) |
| 2026-10-17 | `backend/mvnw.cmd -B test` | **PASS** (89 tests: + cierre automatico de reservas concluidas, `ADR-0023`) |
| 2026-10-17 | `backend/mvnw.cmd -B test` | **PASS** (96 tests: + calificaciones de reservas finalizadas, `ADR-0024`; `CalificacionTest` 7/7) |
| 2026-10-17 | `backend/mvnw.cmd -B test` | **PASS** (104 tests: + reclamos y avisos in-app, `ADR-0025`; `ReclamoNotificacionTest` 8/8) |
| 2026-10-17 | `backend/mvnw.cmd -B test` | **PASS** (109 tests: + moderacion de reclamos, `ADR-0026`; `ModeracionReclamoTest` 5/5) |
| 2026-10-17 | `backend/mvnw.cmd -B test` | **PASS** (113 tests: + administracion de plataforma, `ADR-0027`; `AdminPlataformaTest` 4/4) |
| 2026-10-17 | `backend/mvnw.cmd -B test` | **PASS** (117 tests: + usuarios, roles y estado de cuenta, `ADR-0028`; `AdminUsuariosTest` 4/4) |
| 2026-10-17 | `backend/mvnw.cmd -B test` | **PASS** (121 tests: + cocheras y promociones, `ADR-0029`; `AdminCocherasPromocionesTest` 4/4) |
| 2026-10-17 | `backend/mvnw.cmd -B test` | **PASS** (125 tests: + promociones del propietario aplicadas, `ADR-0030`; `PromocionReservaTest` 4/4) |
| 2026-10-17 | `backend/mvnw.cmd -B test` | **PASS** (129 tests: + catalogo avanzado, `ADR-0031`; `CatalogoCanchaTest` 4/4) |
| 2026-10-17 | `backend/mvnw.cmd -B test` | **PASS** (135 tests: + edicion/baja de venues y comparacion de canchas, `ADR-0032`; `EdicionYBajaVenuesTest` 6/6) |
| 2026-10-17 | `backend/mvnw.cmd -B test` | **PASS** (144 tests: + identidad completa, idempotencia/reprogramacion y endurecimiento, `ADR-0033/0034`; 9 tests nuevos) |
| 2026-10-17 | `scripts/smoke.ps1 -BaseUrl http://localhost:8089 -DbPort 3309` | **PASS** (112/112 pasos, + 8 de edicion, baja logica y comparacion; contenedor con la imagen reconstruida) |
| 2026-10-17 | `DB_PORT=3309 bash scripts/smoke.sh http://localhost:8089` | **PASS** (112/112 pasos, Git Bash) |
| 2026-10-17 | `frontend: npm run build` (Node 24.22 portable) | **PASS** (Angular 22.2.3, bundle inicial ~354 kB) |
| 2026-10-17 | `frontend: npm test -- --watch=false` | **PASS** (15/15 Vitest: ApiError, guardas, paletas, comparacion, App) |
| 2026-10-17 | `frontend: npm run test:e2e` (Playwright, dev y Docker 8083) | **PASS** (registro->login->catálogo paginado->calendario semanal->reserva->pago->reclamo->responsive) |
| 2026-10-17 | `frontend: npm run test:e2e:owner` (Playwright, dev y Docker 8083) | **PASS** (validacion OWNER, establecimiento, cancha, fotos, cochera, agenda, promos, admin) |
| 2026-10-17 | `bash scripts/smoke.sh http://localhost:8089` | **PASS** (112/112 con filtro `idEstablecimiento`) |
| 2026-10-17 | `backend: mvnw test` (contenedor JDK 21, MySQL 3309) | **PASS** (144/144 tras paginación, sin regresiones) |
| 2026-10-17 | `frontend: rediseño de detalle + calendario 365d + fix disabled` | **PASS** (build, 15/15 unit, E2E dev/Docker 8083; capturas `detalle-calendario-*.png`) |
| 2026-10-17 | `backend: mvnw -f backend/pom.xml test` (+ `GET /establecimientos/{id}`) | **PASS** (144/144; `EdicionYBajaVenuesTest` 6/6 con 200 propio, 403 ajeno y 404) |
| 2026-10-17 | `frontend: build + npm test` (+ edicion/baja de establecimiento, reenvio de verificacion) | **PASS** (build OK, 15/15 unit) |
| 2026-10-17 | `compose up -d --build backend frontend` + login admin + `/admin/usuarios` | **PASS** (health 200, frontend 200, roles `USER,OWNER,PLATFORM_ADMIN`) |
| 2026-10-17 | `frontend: npm run test:e2e:owner` contra Docker 8083 | **PASS** (20/20 PASS, incluye crear y editar establecimiento desde el panel y modal de roles) |
| 2026-10-17 | `frontend: npm run test:e2e` (usuario) + `scripts/smoke.ps1 -BaseUrl 8089` | **PASS** (14/14 E2E; humo **112/112**, corregidos 2 pasos que dependian del orden del listado) |
| 2026-10-17 | `accesibilidad Playwright`: skip link, foco, contraste, modal y paginador | **PASS** (10/10: outline 3 px, contraste 5.44:1 / 15.39:1, foco atrapado y Escape, 10 filas por pagina) |
| 2026-10-17 | `npm run test:a11y` (estructura de 13 rutas + teclado) | **PASS** (un `h1` por ruta, sin saltos de encabezado, sin controles/campos sin nombre, 25 Tabs estables; capturas `a11y-*.png`) |
| 2026-10-17 | Cierre documental | **OK** (`docs/ACTA_CIERRE.md`; E-42 registrado en `RESULTADOS_PRUEBAS.md`) |
| 2026-10-17 | Fase 1 de mejoras: `/establecimientos/mios`, auto-login tras registro, paginador estable, vista previa con retorno y galería completa, textos de agenda | **PASS** (backend 144/144, humo 112/112, unitarios 15/15, E2E propietario/admin 28/28, a11y 13 rutas OK) |
| 2026-10-18 | Rediseño de fusión: tokens, fuentes locales, shell, home, catálogo, detalle y login | **PASS** (build OK, 15/15 unitarios, E2E usuario 14/14, E2E propietario/admin 28/28, a11y 13 rutas OK, sin overflow a 390px) |
| 2026-10-18 | Favoritos + catálogo enriquecido + paneles con sidebar | **PASS** (backend 146/146, humo 112/112, build OK, E2E usuario 15/15, E2E propietario/admin 28/28, a11y 14 rutas OK) |
| 2026-10-18 | Topbar standalone, Actividades/Cómo funciona, servicios demo y sidebars de panel | **PASS** (backend 146/146, humo 112/112, build OK, E2E usuario 15/15, E2E propietario/admin 28/28, a11y 16 rutas OK) |

Resto de la matriz de pruebas (?13 del prompt): **NOT RUN**.

## 9. PROBLEMAS ABIERTOS

- **Puertos ocupados** en el host: 80, 3306, 3307, 4200, 8080, 8081, 8090. Se usan
  **8088** (backend nativo) y, en Compose, **3309 / 8089 / 8083** (verificados libres y
  parametrizables desde `.env`).
- **Zona horaria (`L-04`)**: `quarkus.jackson.time-zone` y `quarkus.hibernate-orm.jdbc.time-zone`
  no existen en este stack. Hibernate convertia los `Instant` con la zona de la JVM y la lectura
  volvia en UTC (desfase de -5 h). `bookings` ya usa **literales UTC explicitos** (ADR-0014,
  verificado en E-08); `identity` sigue con horas locales autoconsistentes -> **unificar
  criterio** y fijar el `ObjectMapper` en codigo (ADR-0008).
- **Zona de operacion**: `cd.zona-horaria=America/Lima` es la que usa la disponibilidad para
  interpretar la fecha consultada y convertir la ventana de atencion a instantes UTC
  (ADR-0015). Los scripts de humo asumen UTC-5 sin horario de verano para pedir la franja
  de las 12:00 locales.
- **Tests de integracion**: localmente usan la base `canchadirecta` real con datos unicos; el CI
  (`.github/workflows/backend.yml`) levanta un MySQL 8.4 efimero (ver `L-09`).
- **Calificacion concurrente (RN-04)**: resuelta; la violacion de `uq_calificacion_reserva` se
  traduce a `409 CONFLICT` (ADR-0033).
- **Revocacion de sesiones (L-20)**: resuelta con `usuario.token_version` + claim `ver`; cambiar
  rol/estado/contrasena invalida los access tokens vigentes (ADR-0033).
- **F05 fuera de alcance (decision del usuario):** pasarela de pago real y desembolso de
  liquidaciones no se implementan en esta entrega; se mantiene el pago simulado y quedan
  documentados en `L-12`.
- **El servicio `frontend` de Compose no se puede construir** hasta que exista la app
  Angular (Fase 4). Para levantar solo backend + MySQL usar
  `docker compose ... up mysql backend`.
- **Baja logica sin reactivacion**: `DELETE` de establecimiento/cancha solo se revierte con SQL;
  la ficha publica sigue respondiendo `200` con `estado = INACTIVA` (el listado y las reservas ya
  la excluyen).

### Como levantar el stack verificado (Modo C)

```powershell
cd canchadirecta
docker compose -f compose.yml -f compose.mysql.yml up -d --build mysql backend
./scripts/smoke.ps1 -BaseUrl http://localhost:8089 -DbPort 3309   # 112/112
DB_PORT=3309 bash scripts/smoke.sh http://localhost:8089          # 112/112 (Git Bash/WSL/Linux)
docker compose -f compose.yml -f compose.mysql.yml down
```

## 10. CREDENCIALES REQUERIDAS (solo nombres de variables)

`DB_HOST` `DB_PORT` `DB_NAME` `DB_USER` `DB_PASSWORD` `DB_MIG_USER`
`DB_MIG_PASSWORD` `DB_ADMIN_USER` `DB_ADMIN_PASSWORD` `QUARKUS_HTTP_PORT`
`ADMIN_CORREO` `ADMIN_PASSWORD` (cuenta superadministrador local, tambien para E2E).
Valores reales unicamente en `canchadirecta/.env` (gitignored). Nunca en Git.

## 11. PROXIMA ACCION EXACTA

Backend y frontend estan operativos y verificados. Para cerrar Fase 4 queda pulido:

**Estado:** entrega cerrada el 2026-10-17 y Fase 1 de mejoras aplicada. Acta:
`docs/ACTA_CIERRE.md`. Tareas opcionales posteriores (no bloquean la entrega):

1. Revision manual con lector de pantalla (NVDA/Narrator).
2. Script idempotente de bootstrap del superadministrador.
3. Cambiar la clave local del superadministrador antes de un despliegue real.
4. Fase 2: modo oscuro, asistente de configuracion y rediseno de bloqueos.

Cuenta superadministrador local (roles `USER`, `OWNER`, `PLATFORM_ADMIN`):
`admin@canchadirecta.pe`; la clave esta en `.env` (`ADMIN_PASSWORD`) y se usa tambien en los
E2E del propietario/admin.

Notas de la última sesión:
- Node 22.13 del host no cumple Angular 22 (`>=22.22.3`); se usó un Node 24.22 portable en
  `%TEMP%\opencode\node-v24.22.0-win-x64`. Instalar Node 24 LTS si se trabaja sin el portable.
- `frontend/proxy.conf.json` apunta a `http://localhost:8088`; en Docker el proxy va a
  `backend:8080`. Para E2E contra el backend de Compose usar `--proxy-config` hacia 8089.
- E2E: `npm run test:e2e` y `npm run test:e2e:owner` (requiere `ADMIN_CORREO`/`ADMIN_PASSWORD`);
  ver `frontend/e2e/README.md`.
- El backend **no** expone `GET /establecimientos/mios` (L-22); el panel pide/crea el ID una vez.
  Para precargar la edicion existe `GET /establecimientos/{id}` (solo propietario). Las canchas
  propias sí se listan con `GET /canchas?idEstablecimiento=...`.
- **Detalle de cancha rediseñado**: galería con fallback, bloques de información, reserva sticky
  y calendario semanal dentro de la columna principal; en móvil la reserva va primero. El botón
  `Reservar` y la paginación ahora respetan `disabled` (antes la directiva `cdButton` lo borraba).
- **Ventana de reserva**: 365 días en backend (`cd.reservas.dias-anticipacion-max`) y
  `APP_CONFIG.diasAnticipacionMax` en frontend; mantener ambos valores sincronizados.

### Diseno aprobado (2026-10-17)

- Componentes **propios sobre Angular CDK**; no Angular Material como capa visual (ADR-0035).
- Identidad **parametrizable por paletas/tokens** (`[data-palette]` + CSS vars); inicial navy +
  turquesa; `ThemeService` persiste la eleccion. Ningun componente usa hex literales.
- Sesion: **access en memoria + refresh en `sessionStorage`** con rotacion; `401` limpia sesion.
- Detalle completo (tokens, componentes, rutas, estados, a11y, responsive y slices) en
  **`docs/UX_FLOWS.md`**; decision en **ADR-0035**. El contrato REST resumido para el frontend
  esta en **`docs/CONTRATOS_API_FRONTEND.md`** (83 endpoints).

Orden original (ya ejecutado hasta el nucleo): scaffold + shell/navegacion -> auth
(login/registro/verificacion/perfil) -> catalogo y comparacion -> reserva + pago simulado ->
panel del propietario (venues, agenda, reservas recibidas, promociones) -> admin ->
reclamos/notificaciones.

Exclusiones de alcance vigentes (no bloquean Fase 4): pasarela/desembolso reales (F05), penalidad
parcial/no-show (L-12) y excepciones de agenda por fecha (L-13).

**Como ejecutar las pruebas** (desde `canchadirecta`, con `JAVA_HOME` = JDK 21): cargar las
variables de `.env` en el entorno y ejecutar `backend\mvnw.cmd -B test`. Si se ejecuta Maven
desde `backend`, el fork de Surefire no ve el `.env` de la raiz y la app no arranca (error de
`mp.jwt.verify.publickey.location`).
