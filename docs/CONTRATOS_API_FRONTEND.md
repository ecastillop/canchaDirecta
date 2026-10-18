# Contratos API — Backend CanchaDirecta (para frontend Angular)

Generado a partir de los `*Resource.java`, sus DTOs y los mappers del backend.
Base URL local: `http://localhost:8088` (`quarkus.http.port`, por defecto 8088).
Todas las rutas ya incluyen el prefijo `/api/v1` (no hay `@ApplicationPath` global).

Convenciones de la tabla:

- **Auth**: `público` = sin token; `@Authenticated` = cualquier JWT válido; `PLATFORM_ADMIN` = rol exigido.
- `*` = campo obligatorio en el request. Los tipos son los tipos Java serializados por Jackson
  (`Instant` = ISO-8601 UTC, `LocalDate` = `AAAA-MM-DD`, `LocalTime` = `HH:mm:ss`, `BigDecimal` = número).
- No existe ninguna anotación `@JsonProperty` en el backend: el nombre JSON coincide con el nombre del record.

---

## 0. Convenciones transversales

### 0.1 Autenticación / JWT

Cabecera en endpoints protegidos: `Authorization: Bearer <accessToken>`.

Login (`POST /api/v1/auth/login`) y refresh devuelven `TokenResponse`:

| Campo | Tipo | Descripción |
|---|---|---|
| `accessToken` | string | JWT HS256 firmado |
| `refreshToken` | string | token opaco (48 bytes Base64URL); se rota en cada refresh |
| `tokenType` | string | `"Bearer"` |
| `expiresIn` | number | segundos de vida del access token (1800 por defecto) |

Claims del access token (emitidos en `SmallryeJwtEmisorTokens`): `iss="canchadirecta"`,
`sub=<idUsuario como string>`, `correo`, `ver` (versión de token), `groups` (roles:
`USER`, `OWNER`, `PLATFORM_ADMIN`), `exp`. Si `ver` no coincide con la versión actual del
usuario, el filtro responde `401 UNAUTHENTICATED` (sesión revocada).

Refresh token: vigencia 30 días (`cd.auth.refresh-days`). Un refresh reutilizado revoca todas
las sesiones del usuario.

### 0.2 Formato de error

Mapper global (`DomainExceptionMapper`) — cuerpo RFC 9457:

```json
{
  "type": "about:blank",
  "title": "Conflicto",
  "status": 409,
  "code": "SLOT_UNAVAILABLE",
  "detail": "La franja ya esta reservada."
}
```

| `code` | HTTP |
|---|---|
| `VALIDATION_ERROR` | 400 |
| `EMAIL_ALREADY_REGISTERED` | 409 |
| `INVALID_CREDENTIALS` | 401 |
| `UNAUTHENTICATED` | 401 |
| `FORBIDDEN` | 403 |
| `NOT_FOUND` | 404 |
| `CONFLICT` | 409 |
| `EMAIL_NOT_VERIFIED` | 403 |
| `TOO_MANY_ATTEMPTS` | 429 |
| `SLOT_UNAVAILABLE` | 409 |
| `RESOURCE_BUSY` | 409 |
| otros códigos de dominio | 422 |

Errores de validación Bean Validation (`ValidationExceptionMapper`) añaden `errors`:

```json
{
  "type": "about:blank", "title": "Solicitud invalida", "status": 400,
  "code": "VALIDATION_ERROR", "detail": "Uno o mas campos no son validos.",
  "errors": [{ "campo": "crearReservaRequest.inicio", "mensaje": "no debe ser nulo" }]
}
```

`PessimisticLockException` => `409` con `code="RESOURCE_BUSY"`.
**Ambigüedad:** los `401`/`403` generados por la capa de seguridad de Quarkus (token ausente,
inválido o rol insuficiente) no tienen `ExceptionMapper` propio; pueden llegar sin el cuerpo
JSON anterior. El `401` por token revocado sí usa el JSON (`code=UNAUTHENTICATED`).

### 0.3 Paginación

No existe paginación por páginas ni envelope `{content,total,...}`. Los listados devuelven un
array JSON plano y aceptan `limite` (número, opcional) con estos valores por defecto:
canchas 50, distritos 200, reservas/reservas recibidas 50, liquidaciones sin límite,
calificaciones 20, reclamos 20, notificaciones 20, admin 50 (auditoría 100). El backend no
devuelve metadatos de paginación.

---

## 1. Identidad y cuentas

### AuthResource — `/api/v1/auth` (público)

| Método y ruta | Roles | Request | Response | Notas |
|---|---|---|---|---|
| `POST /api/v1/auth/register` | público | `RegistrarUsuarioRequest`: `nombres*` (≤80), `apellidos*` (≤80), `correo*` (email ≤150), `telefono` (≤20), `password*` (8..100), `confirmacionPassword*` | `201 UsuarioResponse`: `id`, `nombres`, `apellidos`, `correo`, `estado`, `roles[]` | `Location: /api/v1/users/{id}`. 409 `EMAIL_ALREADY_REGISTERED`. |
| `POST /api/v1/auth/login` | público | `IniciarSesionRequest`: `correo*`, `password*` | `200 TokenResponse` | 401 `INVALID_CREDENTIALS`; 403 `EMAIL_NOT_VERIFIED` (desactivado por defecto); 429 `TOO_MANY_ATTEMPTS` tras 5 fallos. |
| `POST /api/v1/auth/refresh` | público | `RefreshTokenRequest`: `refreshToken*` | `200 TokenResponse` | 401 `UNAUTHENTICATED` si es inválido/expirado/reutilizado; 403 si la cuenta no está activa. |
| `POST /api/v1/auth/logout` | público | `LogoutRequest`: `refreshToken` (opcional) | `204` sin cuerpo | Revoca el refresh si se envía. |
| `POST /api/v1/auth/email/verify` | público | `VerificarCorreoRequest`: `token*` | `204` sin cuerpo | 400 `VALIDATION_ERROR` si el token es inválido/expirado. |
| `POST /api/v1/auth/email/resend` | público | `ReenviarVerificacionRequest`: `correo*` | `202` sin cuerpo | No revela si la cuenta existe. |
| `POST /api/v1/auth/password-reset/request` | público | `SolicitarRecuperacionRequest`: `correo*` | `202` sin cuerpo | No revela si la cuenta existe. |
| `POST /api/v1/auth/password-reset/confirm` | público | `ConfirmarRecuperacionRequest`: `token*`, `nuevaPassword*` (≤100), `confirmacionPassword*` | `204` sin cuerpo | 400 si no coincide o el token es inválido. |

### MeResource — `/api/v1/users` (`@Authenticated`)

| Método y ruta | Roles | Request | Response | Notas |
|---|---|---|---|---|
| `GET /api/v1/users/me` | autenticado | — | `200` objeto inline: `id` (number), `correo`, `nombres`, `apellidos`, `telefono`, `estado` (`ACTIVO`/`INACTIVO`/`BLOQUEADO`), `correoVerificado` (boolean), `roles[]`, `tokenId` (jti) | 404 `NOT_FOUND` si el usuario no existe. |
| `PUT /api/v1/users/me` | autenticado | `ActualizarPerfilRequest`: `nombres*` (≤80), `apellidos*` (≤80), `telefono` (≤20) | `200` mismo objeto inline que `GET /users/me` | No permite cambiar correo ni roles. |

### SystemResource — `/api/v1/system` (público)

| Método y ruta | Roles | Request | Response | Notas |
|---|---|---|---|---|
| `GET /api/v1/system/ping` | público | — | `200` `{application:"canchadirecta", status:"ok", timestamp:<ISO-8601 con offset>}` | Diagnóstico. |

---

## 2. Establecimientos, canchas y agenda

### VenuesResource — `/api/v1` (`@Authenticated` por método)

| Método y ruta | Roles | Request | Response | Notas |
|---|---|---|---|---|
| `POST /api/v1/establecimientos` | autenticado | `CrearEstablecimientoRequest`: `documentoIdentidad*` (≤20), `idDistrito*`, `nombre*` (≤120), `direccion*` (≤200), `referencia` (≤200) | `201 RecursoCreadoResponse`: `{id}` | Crea/asegura el perfil de propietario del usuario. `Location: /api/v1/establecimientos/{id}`. |
| `GET /api/v1/establecimientos/mios` | autenticado | — | `200 [Establecimiento]`: establecimientos del usuario, activos primero | Lista vacía si el usuario aún no es propietario. |
| `GET /api/v1/establecimientos/{id}` | autenticado (propietario) | — | `200 Establecimiento`: `id`, `idPropietario`, `idDistrito`, `nombre`, `direccion`, `referencia`, `estado` | 403 si no es propietario; 404 si no existe. |
| `PUT /api/v1/establecimientos/{id}` | autenticado (propietario) | `ActualizarEstablecimientoRequest`: `idDistrito*`, `nombre*` (≤120), `direccion*` (≤200), `referencia` (≤200) | `200 Establecimiento`: `id`, `idPropietario`, `idDistrito`, `nombre`, `direccion`, `referencia`, `estado` (`ACTIVO`/`INACTIVO`) | 403 `FORBIDDEN` si no es propietario; 404 si no existe. |
| `DELETE /api/v1/establecimientos/{id}` | autenticado (propietario) | — | `204` sin cuerpo | Baja lógica + canchas. 409 si hay reservas vigentes; 404 si ya estaba inactivo. |
| `POST /api/v1/establecimientos/{id}/canchas` | autenticado (propietario) | `CrearCanchaRequest`: `nombre*` (≤100), `descripcion` (≤500), `capacidad*` (1..100), `precioBase*` (≥0) | `201 RecursoCreadoResponse` | `Location: /api/v1/canchas/{id}`. 403 si no es propietario. |
| `GET /api/v1/canchas` | público | Query: `idDistrito`, `idEstablecimiento`, `idDeporte`, `precioMax`, `q` (texto), `limite` (def. 50, max 100), `desplazamiento` (def. 0) | `200 List<CanchaResumen>`: `id`, `idEstablecimiento`, `nombre`, `capacidad`, `precioBase`, `estado`, `idDistrito`, `distrito`, `establecimiento`, `fotoPrincipal`, `promedioCalificacion`, `totalCalificaciones`, `deportes[]` | Búsqueda pública. Devuelve `X-Total-Count` con el total filtrado (paginación numérica). |
| `GET /api/v1/favoritos` | autenticado | - | `200 List<Long>` con los IDs de canchas favoritas | Orden: más recientes primero. |
| `POST /api/v1/favoritos/{idCancha}` | autenticado | - | `204` sin cuerpo | Idempotente; 404 si la cancha no existe o no está activa. |
| `DELETE /api/v1/favoritos/{idCancha}` | autenticado | - | `204` sin cuerpo | Idempotente. |
| `POST /api/v1/establecimientos/{id}/cocheras` | autenticado (propietario) | `CrearCocheraRequest`: `nombre*` (≤100), `capacidad*` (1..1000), `tarifaHora*` (≥0) | `201 RecursoCreadoResponse` | 403 si no es propietario. |
| `GET /api/v1/establecimientos/{id}/cocheras` | autenticado (propietario) | — | `200 List<Cochera>`: `id`, `idEstablecimiento`, `nombre`, `capacidad`, `tarifaHora`, `estado` (`ACTIVA`/`INACTIVA`) | Solo cocheras del establecimiento propio. |
| `DELETE /api/v1/establecimientos/{id}/cocheras/{idCochera}` | autenticado (propietario) | — | `204` sin cuerpo | Baja lógica. |

### CatalogoResource — `/api/v1` (público)

| Método y ruta | Roles | Request | Response | Notas |
|---|---|---|---|---|
| `GET /api/v1/ubicaciones/distritos` | público | Query: `q`, `limite` (def. 200) | `200 List<Distrito>`: `id`, `nombre`, `provincia`, `departamento` | |
| `GET /api/v1/deportes` | público | — | `200 List<Item>`: `id`, `nombre` | |
| `GET /api/v1/caracteristicas` | público | — | `200 List<Item>`: `id`, `nombre` | |

### CanchaDetalleResource — `/api/v1/canchas/{idCancha}`

| Método y ruta | Roles | Request | Response | Notas |
|---|---|---|---|---|
| `GET /api/v1/canchas/{idCancha}` | público | — | `200 CanchaDetalleResponse` (ver abajo) | 404 si no existe. |
| `PUT /api/v1/canchas/{idCancha}` | autenticado (propietario) | `ActualizarCanchaRequest`: `nombre*` (≤100), `descripcion` (≤500), `capacidad*` (1..100), `precioBase*` (≥0) | `200 Cancha`: `id`, `idEstablecimiento`, `nombre`, `capacidad`, `precioBase`, `estado` | 403 si no es propietario; 409 si ya existe otra cancha con ese nombre en el establecimiento. |
| `DELETE /api/v1/canchas/{idCancha}` | autenticado (propietario) | — | `204` sin cuerpo | Baja lógica. 409 si hay reservas vigentes; 403/404. |
| `PUT /api/v1/canchas/{idCancha}/deportes` | autenticado (propietario) | `ActualizarIdsRequest`: `ids*` (`List<Long>`, vacío = limpiar) | `200 CanchaDetalleResponse` | Reemplaza el conjunto. 403 si no es propietario. |
| `PUT /api/v1/canchas/{idCancha}/caracteristicas` | autenticado (propietario) | `ActualizarIdsRequest`: `ids*` | `200 CanchaDetalleResponse` | Reemplaza el conjunto. |
| `POST /api/v1/canchas/{idCancha}/fotos` | autenticado (propietario) | `CrearFotoRequest`: `url*` (≤500), `orden` (1..99), `principal` (boolean) | `201 Foto`: `id`, `url`, `orden`, `principal` | `Location: /api/v1/canchas/{id}/fotos/{idFoto}`. 409 `CONFLICT` si `orden` ya existe. |
| `DELETE /api/v1/canchas/{idCancha}/fotos/{idFoto}` | autenticado (propietario) | — | `204` sin cuerpo | 404 si la foto no existe. |

`CanchaDetalleResponse`:

```json
{
  "id": 1, "nombre": "Cancha 1", "descripcion": "...", "capacidad": 10,
  "precioBase": 80.00, "estado": "ACTIVA",
  "idEstablecimiento": 2, "establecimiento": "Complejo", "direccion": "...", "referencia": "...",
  "distrito": { "id": 1, "nombre": "Miraflores", "provincia": "Lima", "departamento": "Lima" },
  "deportes": [{ "id": 1, "nombre": "Futbol" }],
  "caracteristicas": [{ "nombre": "Césped", "valor": "Sintético" }],
  "fotos": [{ "id": 1, "url": "https://...", "orden": 1, "principal": true }],
  "reputacion": { "total": 12, "promedio": 4.5 }
}
```

### CompararCanchasResource — público

| Método y ruta | Roles | Request | Response | Notas |
|---|---|---|---|---|
| `GET /api/v1/canchas/comparar` | público | Query: `ids*` (2 a 4 ids separados por coma) | `200 List<CanchaDetalleResponse>` en el orden pedido | 400 `VALIDATION_ERROR` si falta `ids` o no es numérico. |

### AgendaResource — `/api/v1/canchas/{idCancha}`

| Método y ruta | Roles | Request | Response | Notas |
|---|---|---|---|---|
| `GET /api/v1/canchas/{idCancha}/horarios` | público | — | `200 List<HorarioCancha>`: `id`, `idCancha`, `diaSemana` (1=lunes..7=domingo), `horaInicio`, `horaFin`, `precio`, `activo` | Solo horarios activos. |
| `POST /api/v1/canchas/{idCancha}/horarios` | autenticado (propietario) | `CrearHorarioRequest`: `diaSemana*` (1..7), `horaInicio*` (`HH:mm:ss`), `horaFin*`, `precio` (≥0, opcional) | `201 RecursoCreadoResponse` | 409 si se solapa con otro horario; 403 si no es propietario. |
| `DELETE /api/v1/canchas/{idCancha}/horarios/{idHorario}` | autenticado (propietario) | — | `204` sin cuerpo | 404 si no existe en esa cancha. |
| `GET /api/v1/canchas/{idCancha}/bloqueos` | autenticado (propietario) | Query: `desde`, `hasta` (ISO-8601 UTC, opcionales) | `200 List<BloqueoCancha>`: `id`, `idCancha`, `inicio`, `fin`, `motivo` | 400 si `desde`/`hasta` no son ISO-8601; 403 si no es propietario. |
| `POST /api/v1/canchas/{idCancha}/bloqueos` | autenticado (propietario) | `CrearBloqueoRequest`: `inicio*` (Instant), `fin*` (Instant), `motivo*` (≤200) | `201 RecursoCreadoResponse` | 409 si choca con reserva u otro bloqueo; 403. |
| `DELETE /api/v1/canchas/{idCancha}/bloqueos/{idBloqueo}` | autenticado (propietario) | — | `204` sin cuerpo | 404 si no existe. |

---

## 3. Reservas, disponibilidad y cocheras

### ReservaResource — `/api/v1/reservas` (`@Authenticated`)

| Método y ruta | Roles | Request | Response | Notas |
|---|---|---|---|---|
| `POST /api/v1/reservas` | autenticado | `CrearReservaRequest`: `canchaId*`, `inicio*` (Instant), `fin*` (Instant), `cocheraId` (opcional), `codigoPromocion` (opcional) | `201 ReservaResponse` | Cabecera opcional `Idempotency-Key` (≤80). 409 `SLOT_UNAVAILABLE` si la franja/cochera no está libre; 409 si la promoción no aplica; 403 si la cochera es de otro establecimiento. |
| `POST /api/v1/reservas/{idReserva}/modificar` | autenticado (titular) | `ModificarReservaRequest`: `inicio*`, `fin*` | `200 ReservaResponse` | Misma duración; 409 si la nueva franja no está libre; 403 si no es titular; 404. |
| `GET /api/v1/reservas/mias` | autenticado | Query: `limite` (def. 50) | `200 List<ReservaResponse>` | Reservas del usuario del JWT. |
| `POST /api/v1/reservas/{idReserva}/cancelar` | autenticado (titular) | — | `200 ReservaResponse` | 409 si ya empezó, ya estaba cancelada/finalizada o falta menos de 2 h; 403 si no es titular. Puede generar reembolso automático. |
| `GET /api/v1/reservas/recibidas` | autenticado (propietario) | Query: `estado`, `idCancha`, `desde`, `hasta` (Instant ISO-8601), `limite` (def. 50, máx. 100) | `200 List<ReservaRecibidaResponse>` (campos de `ReservaResponse` + `cliente`, `telefono`, `pagoEstado`) | `estado` ∈ `PENDIENTE,CONFIRMADA,CANCELADA,RECHAZADA,EXPIRADA,FINALIZADA`; valor inválido => 400. `idCancha` y el rango `desde`/`hasta` (por solape) son filtros opcionales que alimentan la ocupación semanal del panel (ADR-0043); instante mal formado => 400. `cliente`/`telefono` son del titular y `pagoEstado` es el estado del último pago (`APROBADO, RECHAZADO, SOLICITADO`, nulo sin pagos): la bandeja del propietario los muestra (ADR-0044) y `/reservas/mias` no los expone. |
| `POST /api/v1/reservas/{idReserva}/confirmar` | autenticado (propietario) | — | `200 ReservaResponse` | Solo reservas `PENDIENTE` de sus canchas; 409 si ya no está pendiente; 403/404. |
| `POST /api/v1/reservas/{idReserva}/rechazar` | autenticado (propietario) | `RechazarReservaRequest`: `motivo` (≤200, opcional) | `200 ReservaResponse` | Libera la franja y aplica reembolso; 409 si no está pendiente. |
| `POST /api/v1/reservas/{idReserva}/revocar` | autenticado (propietario) | `RechazarReservaRequest`: `motivo` (opcional) | `200 ReservaResponse` | Solo reserva `CONFIRMADA`; reembolso total; 409 si no procede. |

`ReservaResponse`:

```json
{
  "id": 10, "canchaId": 3, "inicio": "2026-11-02T18:00:00Z", "fin": "2026-11-02T19:00:00Z",
  "montoCancha": 80.00, "montoDescuento": 0.00, "estado": "PENDIENTE",
  "fechaCreacion": "2026-10-17T12:00:00Z", "cocheraId": null, "montoCochera": null
}
```

### DisponibilidadResource — público

| Método y ruta | Roles | Request | Response | Notas |
|---|---|---|---|---|
| `GET /api/v1/canchas/{idCancha}/disponibilidad` | público | Query: `fecha*` (`AAAA-MM-DD`), `duracionMinutos` (def. 60) | `200 DisponibilidadResponse`: `canchaId`, `fecha`, `zonaHoraria`, `duracionMinutos`, `franjas[]` con `{inicio,fin,precio}` | 400 si falta `fecha` o el formato es inválido; 404 si la cancha no existe. Fecha en zona `America/Lima`. Ventana máxima: 365 días de anticipación. |

### CocheraResource — público

| Método y ruta | Roles | Request | Response | Notas |
|---|---|---|---|---|
| `GET /api/v1/canchas/{idCancha}/cocheras` | público | Query: `inicio*`, `fin*` (ISO-8601 UTC) | `200 List<CocheraDisponibilidadResponse>`: `id`, `nombre`, `capacidad`, `cupoDisponible`, `tarifaHora` | 400 si falta o es inválido algún instante; 404 si la cancha no existe. |

---

## 4. Pagos y reembolsos

### PagoResource — `/api/v1/reservas` (`@Authenticated`, titular)

| Método y ruta | Roles | Request | Response | Notas |
|---|---|---|---|---|
| `POST /api/v1/reservas/{idReserva}/pago` | autenticado (titular) | `PagoRequest`: `medioPago*` (≤30), `codigoOperacion` (≤80, opcional) | `201 PagoResponse` | `Location: /api/v1/reservas/{idReserva}/pago`. 409 si la reserva no es pagable o ya tiene pago `APROBADO`; 403 si no es titular. |
| `GET /api/v1/reservas/{idReserva}/pago` | autenticado (titular) | — | `200 PagoResponse` | Último pago con sus reembolsos. 404 si no hay pago; 403 si no es titular. |

`PagoResponse`:

```json
{
  "id": 1, "reservaId": 10, "monto": 80.00, "medioPago": "TARJETA",
  "codigoOperacion": "OP-123", "estado": "APROBADO",
  "fechaPago": "2026-10-17T12:05:00Z",
  "reembolsos": [{
    "id": 1, "monto": 80.00, "motivo": "...", "estado": "PROCESADO",
    "fechaSolicitud": "2026-10-18T10:00:00Z", "fechaResolucion": "2026-10-18T10:00:00Z"
  }]
}
```

Estados: pago `PENDIENTE|APROBADO|RECHAZADO|REEMBOLSADO`; reembolso `SOLICITADO|APROBADO|RECHAZADO|PROCESADO`.

---

## 5. Liquidaciones

### LiquidacionResource — `/api/v1/liquidaciones` (`@Authenticated`, propietario)

| Método y ruta | Roles | Request | Response | Notas |
|---|---|---|---|---|
| `POST /api/v1/liquidaciones` | autenticado (propietario) | `GenerarLiquidacionRequest`: `periodoInicio*` (`AAAA-MM-DD`), `periodoFin*` | `201 LiquidacionResponse` | 400 si el periodo es inválido/invertido. 409 si el usuario no tiene perfil de propietario o no hay reservas pagadas y confirmadas sin liquidar. |
| `GET /api/v1/liquidaciones` | autenticado (propietario) | — | `200 List<LiquidacionResponse>` (sin `detalles`; array vacío) | Más recientes primero. |
| `GET /api/v1/liquidaciones/{idLiquidacion}` | autenticado (propietario) | — | `200 LiquidacionResponse` con `detalles[]` | 403 si la liquidación es de otro propietario; 404. |

`LiquidacionResponse`: `id`, `periodoInicio`, `periodoFin`, `montoBruto`, `montoComision`,
`montoNeto`, `estado` (`PENDIENTE|PROCESADA|PAGADA`), `detalles[]` con
`{reservaId, montoReserva, porcentajeComision, montoComision, montoNeto}`.

---

## 6. Engagement: calificaciones, reclamos y notificaciones

### CalificacionReservaResource — `/api/v1/reservas` (`@Authenticated`)

| Método y ruta | Roles | Request | Response | Notas |
|---|---|---|---|---|
| `POST /api/v1/reservas/{idReserva}/calificacion` | autenticado (titular) | `CalificarReservaRequest`: `puntaje*` (1..5), `comentario` (≤500) | `201 CalificacionResponse`: `id`, `reservaId`, `puntaje`, `comentario`, `fechaRegistro` | 409 si la reserva no está `FINALIZADA` o ya fue calificada; 403 si no es titular; 404. |

### CalificacionesCanchaResource — público

| Método y ruta | Roles | Request | Response | Notas |
|---|---|---|---|---|
| `GET /api/v1/canchas/{idCancha}/calificaciones` | público | Query: `limite` (def. 20) | `200 CalificacionesCanchaResponse`: `canchaId`, `total`, `promedio` (número), `calificaciones[]` (`CalificacionResponse`) | 404 si la cancha no existe. |

### ReclamoResource — `/api/v1/reclamos` (`@Authenticated`)

| Método y ruta | Roles | Request | Response | Notas |
|---|---|---|---|---|
| `POST /api/v1/reclamos` | autenticado | `CrearReclamoRequest`: `idReserva` (opcional, debe ser propia), `asunto*` (≤150), `descripcion*` (≤800) | `201 ReclamoResponse`: `id`, `idReserva`, `asunto`, `descripcion`, `estado`, `fechaRegistro` | 403 si la reserva es ajena; 404 si la reserva no existe. |
| `GET /api/v1/reclamos/mios` | autenticado | Query: `limite` (def. 20) | `200 List<ReclamoResponse>` | Más recientes primero. |
| `GET /api/v1/reclamos/{idReclamo}` | autenticado (titular) | — | `200 ReclamoDetalleResponse` = `ReclamoResponse` + `seguimientos[]` `{id,comentario,fecha}` | 403 si es ajeno; 404. |
| `POST /api/v1/reclamos/{idReclamo}/seguimientos` | autenticado (titular) | `ComentarReclamoRequest`: `comentario*` (≤800) | `201 SeguimientoResponse`: `id`, `comentario`, `fecha` | 409 si el reclamo está `CERRADO`; 403/404. |

Estados de reclamo: `ABIERTO → EN_REVISION → RESUELTO → CERRADO`.

### AdminReclamoResource — `/api/v1/admin/reclamos` (`@RolesAllowed("PLATFORM_ADMIN")`)

| Método y ruta | Roles | Request | Response | Notas |
|---|---|---|---|---|
| `GET /api/v1/admin/reclamos` | PLATFORM_ADMIN | Query: `estado`, `limite` (def. 50) | `200 List<AdminReclamoResponse>`: `id`, `idUsuario`, `idReserva`, `asunto`, `descripcion`, `estado`, `fechaRegistro` | |
| `GET /api/v1/admin/reclamos/{idReclamo}` | PLATFORM_ADMIN | — | `200 AdminReclamoDetalleResponse` = cabecera anterior + `seguimientos[]` | 404. |
| `POST /api/v1/admin/reclamos/{idReclamo}/estado` | PLATFORM_ADMIN | `CambiarEstadoReclamoRequest`: `estado*`, `comentario` (≤800) | `200 AdminReclamoResponse` | 409 si la transición no es válida o el reclamo cambió; 404. |
| `POST /api/v1/admin/reclamos/{idReclamo}/seguimientos` | PLATFORM_ADMIN | `ComentarReclamoRequest`: `comentario*` (≤800) | `201 SeguimientoResponse` | 409 si el reclamo está cerrado. |

### NotificacionResource — `/api/v1/notificaciones` (`@Authenticated`)

| Método y ruta | Roles | Request | Response | Notas |
|---|---|---|---|---|
| `GET /api/v1/notificaciones` | autenticado | Query: `limite` (def. 20) | `200 NotificacionesResponse`: `noLeidas`, `total`, `notificaciones[]` `{id,titulo,mensaje,leida,fechaEnvio}` | Más recientes primero. |
| `POST /api/v1/notificaciones/{idNotificacion}/leida` | autenticado | — | `204` sin cuerpo | 404 si no existe o es ajena. |

---

## 7. Propietarios y administración de plataforma

### PropietarioResource — `/api/v1/propietarios` (`@Authenticated`)

| Método y ruta | Roles | Request | Response | Notas |
|---|---|---|---|---|
| `POST /api/v1/propietarios/solicitudes` | autenticado | `SolicitarPropietarioRequest`: `documentoIdentidad*` (≤20) | `201 PropietarioAdmin` (serializado directo): `id`, `idUsuario`, `nombreCompleto`, `correo`, `documento`, `estadoValidacion` | Si ya existe solicitud con el mismo documento, devuelve `201` con la existente. 409 si existe con otro documento o el documento ya pertenece a otro propietario. Sin cabecera `Location`. |
| `GET /api/v1/propietarios/me` | autenticado | — | `200 PropietarioAdmin` | 404 si no tiene solicitud. |

### PromocionPropietarioResource — `/api/v1/establecimientos/{idEstablecimiento}/promociones` (`@Authenticated`)

| Método y ruta | Roles | Request | Response | Notas |
|---|---|---|---|---|
| `GET /api/v1/establecimientos/{idEstablecimiento}/promociones` | autenticado (propietario) | Query: `limite` (def. 50) | `200 List<AdminPromocionResponse>` (ver 7.4) | 403 si el establecimiento es ajeno. |
| `POST /api/v1/establecimientos/{idEstablecimiento}/promociones` | autenticado (propietario) | `CrearPromocionPropietarioRequest`: `codigo*` (≤30), `descripcion*` (≤200), `tipoDescuento*` (`PORCENTAJE`/`MONTO`), `valor*` (>0), `fechaInicio*` (`AAAA-MM-DD`), `fechaFin*` | `201 AdminPromocionResponse` | Asocia todas las canchas del establecimiento; 409 si el código existe o el establecimiento no tiene canchas; 403. |
| `POST /api/v1/establecimientos/{idEstablecimiento}/promociones/{idPromocion}/estado` | autenticado (propietario) | `CambiarEstadoSimpleRequest`: `estado*` (`ACTIVA`/`INACTIVA`) | `200 AdminPromocionResponse` | 409 si ya está en ese estado; 403/404. |

### 7.3 AdminUsuarioResource — `/api/v1/admin/usuarios` (`@RolesAllowed("PLATFORM_ADMIN")`)

| Método y ruta | Roles | Request | Response | Notas |
|---|---|---|---|---|
| `GET /api/v1/admin/usuarios` | PLATFORM_ADMIN | Query: `correo`, `estado`, `rol`, `limite` (def. 50) | `200 List<AdminUsuarioResponse>`: `id`, `nombreCompleto`, `correo`, `telefono`, `estado`, `roles[]`, `fechaRegistro` | |
| `GET /api/v1/admin/usuarios/{idUsuario}` | PLATFORM_ADMIN | — | `200 AdminUsuarioResponse` | 404. |
| `POST /api/v1/admin/usuarios/{idUsuario}/roles` | PLATFORM_ADMIN | `CambiarRolesRequest`: `roles*` (lista no vacía de strings) | `200 AdminUsuarioResponse` | 409 si dejaría la plataforma sin admin activo; 404. |
| `POST /api/v1/admin/usuarios/{idUsuario}/estado` | PLATFORM_ADMIN | `CambiarEstadoUsuarioRequest`: `estado*` (`ACTIVO`/`INACTIVO`/`BLOQUEADO`) | `200 AdminUsuarioResponse` | 409 si ya está en ese estado o es el último admin activo; 404. |

### 7.4 AdminPropietarioResource — `/api/v1/admin/propietarios` (`PLATFORM_ADMIN`)

| Método y ruta | Roles | Request | Response | Notas |
|---|---|---|---|---|
| `GET /api/v1/admin/propietarios` | PLATFORM_ADMIN | Query: `estado`, `limite` (def. 50) | `200 List<AdminPropietarioResponse>`: `id`, `idUsuario`, `nombreCompleto`, `correo`, `documento`, `estadoValidacion` (`PENDIENTE|VALIDADO|RECHAZADO`) | |
| `POST /api/v1/admin/propietarios/{idPropietario}/validacion` | PLATFORM_ADMIN | `CambiarValidacionRequest`: `estado*` (`VALIDADO`/`RECHAZADO`), `motivo` (≤500, obligatorio al rechazar) | `200 AdminPropietarioResponse` | 409 si la transición no procede o cambió; 404. |

### 7.5 AdminPromocionResource — `/api/v1/admin/promociones` (`PLATFORM_ADMIN`)

| Método y ruta | Roles | Request | Response | Notas |
|---|---|---|---|---|
| `GET /api/v1/admin/promociones` | PLATFORM_ADMIN | Query: `estado`, `limite` (def. 50) | `200 List<AdminPromocionResponse>`: `id`, `codigo`, `descripcion`, `tipoDescuento`, `valor`, `fechaInicio`, `fechaFin`, `estado`, `canchas[]` | |
| `POST /api/v1/admin/promociones` | PLATFORM_ADMIN | `CrearPromocionRequest`: `codigo*`, `descripcion*`, `tipoDescuento*`, `valor*` (>0), `fechaInicio*`, `fechaFin*`, `canchas` (lista de ids, opcional) | `201 AdminPromocionResponse` | 409 si el código existe; 404 si alguna cancha no existe. |
| `POST /api/v1/admin/promociones/{idPromocion}/estado` | PLATFORM_ADMIN | `CambiarEstadoSimpleRequest`: `estado*` (`ACTIVA`/`INACTIVA`) | `200 AdminPromocionResponse` | 409 si ya está en ese estado; 404. |

### 7.6 Supervisión de solo lectura y auditoría (`PLATFORM_ADMIN`)

| Método y ruta | Roles | Request | Response | Notas |
|---|---|---|---|---|
| `GET /api/v1/admin/reservas` | PLATFORM_ADMIN | Query: `estado`, `limite` (def. 50) | `200 List<AdminSupervisionResponse.Reserva>`: `id`, `cancha`, `establecimiento`, `titular`, `inicio`, `fin`, `montoCancha`, `montoCochera`, `estado` | |
| `GET /api/v1/admin/pagos` | PLATFORM_ADMIN | Query: `estado`, `limite` (def. 50) | `200 List<AdminSupervisionResponse.Pago>`: `id`, `idReserva`, `monto`, `medioPago`, `estado`, `fechaPago`, `reembolsado` | |
| `GET /api/v1/admin/establecimientos` | PLATFORM_ADMIN | Query: `estado`, `limite` (def. 50) | `200 List<AdminSupervisionResponse.Establecimiento>`: `id`, `nombre`, `distrito`, `propietario`, `estado`, `canchas` (number) | |
| `GET /api/v1/admin/cocheras` | PLATFORM_ADMIN | Query: `estado`, `limite` (def. 50) | `200 List<AdminCocheraResponse>`: `id`, `idEstablecimiento`, `establecimiento`, `nombre`, `capacidad`, `tarifaHora`, `estado` | |
| `POST /api/v1/admin/cocheras/{idCochera}/estado` | PLATFORM_ADMIN | `CambiarEstadoSimpleRequest`: `estado*` (`ACTIVA`/`INACTIVA`) | `200 AdminCocheraResponse` | 409 si ya está en ese estado; 404. |
| `GET /api/v1/admin/auditoria` | PLATFORM_ADMIN | Query: `limite` (def. 100) | `200 List<AuditoriaResponse>`: `id`, `idUsuario`, `accion`, `entidad`, `idEntidad`, `detalle`, `fechaEvento` | Más recientes primero. |

---

## 8. Resumen de códigos y notas de integración

- **409** se usa para conflictos de negocio: correo duplicado, franja/cochera ocupada,
  transición de estado inválida, código de promoción duplicado, nombre de cancha duplicado,
  foto con `orden` duplicado, recurso bloqueado por concurrencia, baja con reservas vigentes.
- **403** se usa tanto para rol insuficiente (seguridad) como para "recurso de otro usuario"
  (regla de negocio, `code=FORBIDDEN`).
- **404** en recursos inexistentes; **400** validación de bean/parámetros; **422** para otros
  códigos de dominio sin mapeo HTTP explícito.
- Listados devuelven arrays planos (sin `total`, `page`, etc.). El frontend debe implementar
  su propia paginación cliente o apoyarse en `limite`.
- Fechas: enviar `Instant` en ISO-8601 con `Z` (p. ej. `2026-11-02T18:00:00Z`) y fechas de
  calendario como `2026-11-02`. La zona de operación es `America/Lima`.
- `Idempotency-Key` solo se lee en `POST /api/v1/reservas`; su valor se guarda junto a la
  reserva para evitar duplicados por reintentos.

### Ambigüedades detectadas

1. Los `401`/`403` de la capa Quarkus (token ausente, inválido o rol insuficiente) no pasan
   por un `ExceptionMapper` documentado aquí; el formato del cuerpo no está garantizado.
2. `POST /api/v1/propietarios/solicitudes` devuelve la entidad de dominio `PropietarioAdmin`
   directamente (no un DTO de respuesta) y no incluye cabecera `Location`.
3. El backend no define endpoints de consulta de un pago/reembolso por id ni de listado de
   reembolsos independiente; solo el último pago de una reserva.
4. No hay endpoint para consultar una reserva individual (`GET /reservas/{id}`).
