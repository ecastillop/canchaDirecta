# Seguridad

Alcance del proyecto: aplicacion web academica (Curso Integrador I). Este documento
describe los controles implementados, los supuestos y lo que **no** esta cubierto.

## 1. Autenticacion

| Aspecto | Implementacion | Archivo |
|---|---|---|
| Contrasenas | **bcrypt** con coste 12; nunca se guarda ni se devuelve el texto en claro | `BcryptPasswordHasher` |
| Token de acceso | **JWT HS256**, vigencia 1800 s, `iss=canchadirecta`, `sub=<id_usuario>`, claim `correo` y `groups`=roles | `SmallryeJwtEmisorTokens` |
| Token de refresco | Valor **opaco** de 48 bytes (`SecureRandom`, base64url); se persiste **solo su SHA-256** | `SmallryeJwtEmisorTokens`, `SesionRefreshTest` |
| Rotacion de refresco | Cada uso revoca el token anterior y emite uno nuevo | `RenovarTokenService` |
| Reutilizacion detectada | Si llega un refresco **ya revocado**, se revocan **todas** las sesiones del usuario (posible robo de token) | `RenovarTokenService` |
| Cierre de sesion | Revoca el refresco; idempotente y sin filtrar si el token existia | `CerrarSesionService` |
| Recuperacion de contrasena | Token de un solo uso (`auth_token`, 30 min), al confirmar se revocan todas las sesiones | `SolicitarRecuperacionService`, `ConfirmarRecuperacionService` |
| Endpoints protegidos | `@Authenticated` + `mp.jwt.verify.*`; `/api/v1/users/me` exige `Authorization: Bearer` | `MeResource` |

### Clave de firma
- Formato **JWK** simetrico (`kty: oct`, HS256) en `canchadirecta/.secrets/jwt-signing-key.jwk`.
- El directorio `.secrets/` esta en `.gitignore` y **no se versiona**.
- La ruta se inyecta por entorno (`JWT_SIGN_KEY_LOCATION`) y **no** esta embebida en la imagen:
  en Docker se monta de solo lectura (`./.secrets/jwt-signing-key.jwk:/app/secrets/...`).
- Verificado: `find /app -name '*.jwk'` dentro de la imagen devuelve `0`.
- En un despliegue real: mover la clave a un gestor de secretos y/o usar RS256 con par de claves.

## 2. Proteccion de datos y enumeracion

- **Registro**: un correo ya registrado devuelve **409** (inevitable para informar al usuario).
- **Login**: mismo codigo y mensaje (401 `INVALID_CREDENTIALS` / "Credenciales invalidas")
  tanto si el correo no existe como si la contrasena es incorrecta.
- **Recuperacion**: `POST /password-reset/request` responde **202 siempre**, incluso con un
  correo inexistente, y solo emite token si la cuenta existe y esta activa.
- Los tokens (refresco y de un solo uso) se almacenan **hasheados** (SHA-256); con acceso a la
  base de datos no se pueden usar directamente.

## 3. Autorizacion

- Roles: `USER`, `OWNER`, `PLATFORM_ADMIN` (catalogo `rol`, sembrado en `V4`).
- El JWT transporta los roles en el claim estandar `groups`.
- **Implementado:** *ownership* por recurso en venues y reservas. `POST /api/v1/establecimientos/{id}/canchas`
  exige que el establecimiento pertenezca al usuario (`FORBIDDEN` si no); el titular de una reserva es
  siempre el `sub` del JWT, nunca un campo del cuerpo (evita IDOR); `GET /api/v1/reservas/mias` filtra por
  `sub`. Los scripts de humo verifican el 401 sin token y el 404 de cancha inexistente.
- **Implementado (HU-008-2):** la bandeja del propietario (`GET /api/v1/reservas/recibidas`) y las
  decisiones sobre reservas (`POST /api/v1/reservas/{id}/confirmar|rechazar`) autorizan por recurso
  (`reserva -> cancha -> establecimiento -> propietario -> usuario`): `403` si el usuario no es el
  propietario, incluso siendo el titular de la propia reserva o propietario de otra cancha.
- **Implementado (F07, ADR-0026/0027/0028/0029):** todos los recursos `/api/v1/admin/*` (reclamos,
  usuarios, propietarios, establecimientos, reservas, pagos, auditoria, promociones y cocheras)
  exigen `@RolesAllowed("PLATFORM_ADMIN")`; un token `USER` recibe `403`. El rol se resuelve del
  claim `groups`, de modo que promover a un usuario exige volver a iniciar sesion. El login rechaza
  cuentas `INACTIVO`/`BLOQUEADO`; un token ya emitido sigue vigente hasta expirar (`L-20`).
- **Pendiente** (Fase 3): llevar la misma regla al catalogo, cochera, pagos y liquidaciones, y
  hacer valer el rol `OWNER` (hoy se resuelve por propiedad del recurso, no por el claim `groups`);
  el resto de endpoints de administracion (F07).

## 4. Base de datos

- Dos usuarios con privilegios separados (ADR-0009):
  - `canchadirecta_mig`: DDL, usado unicamente por **Flyway**.
  - `canchadirecta_app`: `SELECT/INSERT/UPDATE/DELETE`, usado por la aplicacion en ejecucion.
- Ambito `localhost` **y** `127.0.0.1` (no se conceden permisos desde `%`).
- Las contrasenas viven solo en `.env` (gitignored) / variables de entorno; los scripts usan
  `MYSQL_PWD` y nunca imprimen credenciales.
- No se toca ninguna otra base del equipo (ver `scripts/db-bootstrap-safe.ps1`).

## 5. Notificaciones (simuladas)

No hay proveedor SMTP. `LogNotificadorCuenta` registra en el log el envio y, por defecto,
**tambien el token en claro** para poder completar el flujo manualmente:

```
cd.notificaciones.registrar-token-en-log=true   # poner en false con correo real
```

> ?? En un despliegue publico esto debe desactivarse y sustituirse por un adaptador con
> proveedor de correo. Es una comodidad de desarrollo, no un control de seguridad.

## 6. Exposicion de la documentacion

Swagger UI y OpenAPI se publican **tambien en el perfil `prod`** (el que usa el contenedor)
porque la documentacion de la API es parte de la entrega. En un despliegue publico:

```properties
%prod.quarkus.swagger-ui.always-include=false
%prod.quarkus.smallrye-openapi.always-include=false
```

o proteger `/q/*` con autenticacion en el proxy inverso.

## 7. CORS

Habilitado **solo en `%dev`**, con origenes explicitos (`http://localhost:4200`,
`http://127.0.0.1:4200`, `http://localhost:8088`), no con `*`. En el despliegue con Nginx, el
frontend y la API comparten origen, por lo que no se necesita CORS.

## 8. Riesgos aceptados / pendientes

| # | Riesgo | Estado |
|---|---|---|
| S-1 | Sin HTTPS (TLS terminado por el proxy en produccion) | Aceptado en local |
| S-2 | Token en el log del notificador simulado | Documentado; desactivable |
| S-3 | Autorizacion por *ownership* de recursos | Implementada en venues, agendas, fotos/atributos, reservas y liquidaciones (tests de `403`) |
| S-4 | Sin limitacion de intentos de login (*rate limiting*) | Implementado en memoria por correo (`LimitadorIntentosLogin`, 5 fallos -> bloqueo de 5 min, configurable). En cluster requeriria almacen compartido |
| S-5 | Sin verificacion de correo efectiva (falta columna de estado) | Implementada: `usuario.correo_verificado`, token `EMAIL_VERIFY`, `POST /auth/email/verify|resend`; el login solo la exige con `cd.auth.requerir-correo-verificado=true` (off por el envio simulado) |
| S-6 | Sin escaneo automatizado de dependencias en CI | Pendiente |
