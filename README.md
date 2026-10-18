# CanchaDirecta

Plataforma de reserva de canchas deportivas. Monolito modular + arquitectura
hexagonal.

- **Backend:** Java 21 + Quarkus 3.40.1 (Maven)
- **Frontend:** Angular 22 (standalone, signals, CDK) - nucleo de Fase 4 implementado
- **Base de datos:** MySQL 8 del host, gestionada con Flyway
- **Pago:** simulador local determinista (sin cobros reales)

> Estado real: ver [`HANDOFF.md`](HANDOFF.md). Este README describe el objetivo y
> los comandos; no acredita por si solo funcionalidad aun no implementada.

## Requisitos verificados en este equipo

| Herramienta | Version detectada | Notas |
|---|---|---|
| SO | Windows 11 (10.0.26200) | PowerShell 5.1 |
| JDK | 21.0.2 en `B:\cursos\java\jdk-21.0.2` | El JDK por defecto es 17 -> exportar `JAVA_HOME` |
| Maven | 3.9.9 (via `mvnw`) | |
| Node / npm | 22.13.0 / 10.9.2 (Angular 22 exige >=22.22.3; se compilo con Node 24 portable) | |
| MySQL | 8.0.41 (servicio `MySQL80`) | Puerto 3306 |
| Docker | 29.4.3 / Compose v5.1.3 | **Daemon no operativo en la sesion** |

## Puesta en marcha (Modo A - MySQL del host + backend nativo)

```powershell
# 1) Variables de entorno
Copy-Item .env.example .env
#    editar .env con las credenciales locales

# 2) Crear base y usuarios (idempotente, no toca otras bases)
$env:DB_ADMIN_USER='root'; $env:DB_ADMIN_PASSWORD='***'
./scripts/db-bootstrap-safe.ps1

# 3) Generar la clave de firma JWT local (gitignored)
./scripts/jwt-key-bootstrap.ps1

# 4) Backend (aplica migraciones Flyway al arrancar)
$env:JAVA_HOME='B:\cursos\java\jdk-21.0.2'
cd backend
./mvnw quarkus:dev          # http://localhost:8088  (Swagger: /q/swagger-ui)
```

- Health: `http://localhost:8088/q/health/ready`
- OpenAPI: `http://localhost:8088/q/openapi`

### Frontend (Angular 22)

Requiere Node `>=22.22.3` (o 24 LTS). Con el backend arriba:

```powershell
cd frontend
npm install
npm start            # http://localhost:4200, proxy /api -> localhost:8088
npm test -- --watch=false
npm run build        # dist/canchadirecta-frontend/browser
```

Detalle de estructura y decisiones: `frontend/README.md` y `docs/ADR-0035`.

### API disponible hoy

| Metodo | Ruta | Descripcion |
|---|---|---|
| GET | `/api/v1/system/ping` | Diagnostico |
| GET | `/q/health/ready` | Estado de la aplicacion |
| POST | `/api/v1/auth/register` | Registro de cuenta |
| POST | `/api/v1/auth/login` | Login (devuelve `accessToken` + `refreshToken`) |
| POST | `/api/v1/auth/refresh` | Renueva el acceso rotando el refresh token |
| POST | `/api/v1/auth/logout` | Revoca el refresh token (204) |
| POST | `/api/v1/auth/password-reset/request` | Solicita el enlace de recuperacion (202 siempre) |
| POST | `/api/v1/auth/password-reset/confirm` | Cambia la contrasena con el token (204) |
| POST | `/api/v1/auth/email/verify` | Verifica el correo con el token `EMAIL_VERIFY` (204) |
| POST | `/api/v1/auth/email/resend` | Reenvia la verificacion (202 siempre) |
| GET | `/api/v1/users/me` | Datos del usuario autenticado (requiere `Authorization: Bearer`) |
| PUT | `/api/v1/users/me` | Actualiza nombres, apellidos y telefono (CU01.5) |
| POST | `/api/v1/propietarios/solicitudes` | Solicita el alta como propietario (`PENDIENTE` de validacion) |
| GET | `/api/v1/propietarios/me` | Estado de la solicitud de propietario de la cuenta |
| POST | `/api/v1/establecimientos` | Registra un establecimiento; el usuario queda como propietario |
| POST | `/api/v1/establecimientos/{id}/canchas` | Registra una cancha (solo el propietario del establecimiento) |
| GET | `/api/v1/canchas` | Lista canchas, filtrable por `idDistrito` (publico) |
| POST | `/api/v1/reservas` | Crea una reserva (`Idempotency-Key` opcional); `409 SLOT_UNAVAILABLE` si la franja esta ocupada |
| GET | `/api/v1/reservas/mias` | Reservas del usuario autenticado |
| GET | `/api/v1/reservas/recibidas?estado=&limite=` | Reservas recibidas en las canchas del propietario (filtro opcional de estado); solo el propietario |
| POST | `/api/v1/reservas/{id}/confirmar` | El propietario confirma una reserva pendiente; `409 CONFLICT` si ya no esta pendiente o ya comenzo |
| POST | `/api/v1/reservas/{id}/modificar` | Reprograma una reserva propia vigente a otra franja de la misma duracion (CU03.7) |
| POST | `/api/v1/reservas/{id}/rechazar` | El propietario rechaza una reserva pendiente (`motivo` opcional) y libera la franja |
| GET | `/api/v1/canchas/{id}/disponibilidad?fecha=AAAA-MM-DD&duracionMinutos=60` | Franjas libres ese dia local segun la agenda de la cancha (publico); la ventana por defecto solo aplica si la cancha no tiene horarios |
| POST | `/api/v1/reservas/{id}/cancelar` | Cancela una reserva propia; `409 CONFLICT` fuera de la ventana minima |
| GET | `/api/v1/canchas/{id}/horarios` | Horarios de atencion activos de la cancha (publico) |
| POST | `/api/v1/canchas/{id}/horarios` | Registra un horario semanal (`diaSemana` 1-7, `horaInicio`, `horaFin`, `precio` opcional); solo el propietario |
| DELETE | `/api/v1/canchas/{id}/horarios/{idHorario}` | Da de baja un horario (baja logica); solo el propietario |
| GET | `/api/v1/canchas/{id}/bloqueos?desde=&hasta=` | Bloqueos operativos de la cancha en un rango; solo el propietario |
| POST | `/api/v1/canchas/{id}/bloqueos` | Bloquea una franja (`inicio`, `fin`, `motivo`); `409 CONFLICT` si choca con una reserva vigente o con otro bloqueo |
| DELETE | `/api/v1/canchas/{id}/bloqueos/{idBloqueo}` | Levanta un bloqueo; solo el propietario |
| GET | `/api/v1/canchas/comparar?ids=1,2` | Ficha de 2 a 4 canchas en el orden pedido (publico, CU02.2) |
| PUT | `/api/v1/establecimientos/{id}` | Edita nombre, direccion, distrito y referencia (solo el propietario, CU04.1) |
| DELETE | `/api/v1/establecimientos/{id}` | Baja logica del complejo y de sus canchas; `409` si hay reservas vigentes (CU04.2) |
| PUT | `/api/v1/canchas/{id}` | Edita nombre, descripcion, capacidad y precio base (solo el propietario, CU04.1) |
| DELETE | `/api/v1/canchas/{id}` | Baja logica de la cancha; `409` si hay reservas vigentes (CU04.2) |

### Docker (stack verificado)

```powershell
cd canchadirecta
docker compose -f compose.yml -f compose.mysql.yml up -d --build mysql backend
# backend   -> http://localhost:8089   (Swagger UI: /q/swagger-ui)
# MySQL     -> localhost:3309
./scripts/smoke.ps1 -BaseUrl http://localhost:8089
docker compose -f compose.yml -f compose.mysql.yml down
```

## Pruebas

```powershell
cd backend
./mvnw test                 # unitarias + integracion contra MySQL real (perfil test)
```

Resultados reales en [`docs/RESULTADOS_PRUEBAS.md`](docs/RESULTADOS_PRUEBAS.md).

## Docker

- `compose.yml` -> frontend (Nginx) + backend; backend apunta al MySQL del host
  via `host.docker.internal`.
- `compose.mysql.yml` -> MySQL aislado en el puerto **3307** (no choca con 3306).

```bash
docker compose up --build -d                              # contra MySQL del host
docker compose -f compose.yml -f compose.mysql.yml up --build -d   # MySQL aislado
```

Detalle en [`docs/DEPLOY_DOCKER.md`](docs/DEPLOY_DOCKER.md).

## Documentacion

`docs/` - requisitos y reconciliacion, trazabilidad, arquitectura, modelo de
datos, API, seguridad, UX, pruebas, despliegue, decisiones (ADRs), limitaciones y
[acta de cierre](docs/ACTA_CIERRE.md).

## Base de datos

- Base: `canchadirecta` (utf8mb4).
- Usuarios: `canchadirecta_mig` (DDL, para Flyway) y `canchadirecta_app`
  (DML minimo, para runtime). Contrasenas solo en `.env` (no versionado).
- El esquema lo versiona Flyway (`backend/src/main/resources/db/migration`).
  **Nunca** se usa `drop-and-create` ni `flyway clean` sobre la base real.
