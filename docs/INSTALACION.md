# Instalacion desde cero

Guia completa para levantar CanchaDirecta en un equipo nuevo, con dos caminos:

- **A. Desde el codigo**: MySQL + backend Quarkus + frontend Angular, con migraciones Flyway.
- **B. Desde el volcado**: restaurar `db/canchadirecta.sql` (esquema + datos) y arrancar.

El volcado y la guia cubren todo lo necesario para reconstruir el sistema.

## 1. Componentes

| Pieza | Tecnologia | Puerto por defecto |
|---|---|---|
| Base de datos | MySQL 8 | 3306 (host) / 3309 (Compose aislado) |
| Backend | Java 21 + Quarkus 3.40.1 (Maven) | 8088 nativo / 8089 contenedor |
| Frontend | Angular 22 (Node 24 LTS) | 4200 dev / 8083 contenedor |
| Proxy web | Nginx dentro del contenedor del frontend | `/api` hacia el backend |

## 2. Requisitos

| Herramienta | Version minima | Verificacion |
|---|---|---|
| JDK | 21 | `java -version` (el proyecto no compila con 17) |
| Maven | 3.9 (o `mvnw`) | incluido en `backend/mvnw.cmd` |
| Node.js | 22.22.3 (recomendado 24 LTS) | `node -v`; Angular 22 no soporta 22.13 |
| npm | 10+ | `npm -v` |
| MySQL Server | 8.0+ (8.4 en Compose) | servicio activo en 3306 |
| Cliente MySQL | `mysql` y `mysqldump` en el PATH | `mysql --version` |
| Docker Desktop | opcional (modos Compose) | `docker compose version` |
| PowerShell | 5.1+ (scripts `.ps1`) | `$PSVersionTable` |

> En Windows, el cliente MySQL suele estar en
> `C:\Program Files\MySQL\MySQL Server 8.0\bin`; agregalo al `PATH` para `mysql`/`mysqldump`.

## 3. Preparar el proyecto

```powershell
git clone <repositorio> canchadirecta
cd canchadirecta
Copy-Item .env.example .env
```

Edita `.env` con valores reales. Claves imprescindibles:

```ini
DB_HOST=127.0.0.1
DB_PORT=3306
DB_NAME=canchadirecta
DB_USER=canchadirecta_app
DB_PASSWORD=...
DB_MIG_USER=canchadirecta_mig
DB_MIG_PASSWORD=...
DB_ADMIN_USER=root
DB_ADMIN_PASSWORD=...
JWT_SIGN_KEY_LOCATION=./.secrets/jwt-signing-key.jwk
ADMIN_CORREO=admin@canchadirecta.pe
ADMIN_PASSWORD=...
QUARKUS_HTTP_PORT=8088
BACKEND_PORT=8089
FRONTEND_PORT=8083
```

`.env` esta ignorado por Git: nunca lo subas.

## 4. Base de datos

### 4.1 Crear la base y los usuarios (necesario en ambos caminos)

```powershell
.\scripts\db-bootstrap-safe.ps1
```

Es idempotente: crea `DB_NAME` con `utf8mb4`, el usuario de runtime (`DB_USER`, permisos
SELECT/INSERT/UPDATE/DELETE) y el de migracion (`DB_MIG_USER`, DDL). No toca otras bases.

### 4.2 Camino A: esquema desde Flyway

El backend aplica `V1..V18` al arrancar (Flyway es dueno del esquema; Hibernate solo valida).
No hay que ejecutar SQL a mano. Si quieres datos de demostracion:

```powershell
.\scripts\demo-reset.ps1 -Force
```

Ese script respalda, recrea la base, aplica migraciones y siembra admin, propietario, operador,
cliente, 4 sedes, 16 canchas, reservas, bloqueo, reclamo y actividad.

### 4.3 Camino B: restaurar el volcado `db/canchadirecta.sql`

El repositorio incluye un volcado completo (esquema + datos) generado con `db-export.ps1`:

```powershell
.\scripts\db-import.ps1 -Force
# o contra otra base:
.\scripts\db-import.ps1 -Archivo db\canchadirecta.sql -Base canchadirecta_demo -Force
```

- Incluye las 45 tablas (44 del modelo + `flyway_schema_history`) y los datos del entorno de
  desarrollo: cuentas demo, `elvis@`, usuarios `*.dev@` y registros de pruebas E2E.
- La cabecera del archivo indica fecha y origen.
- Si restauras sobre una base nueva, ejecuta despues `db-bootstrap-safe.ps1` para crear los
  usuarios de la aplicacion y confirmar permisos.

### 4.4 Exportar de nuevo

```powershell
.\scripts\db-export.ps1                             # db\canchadirecta.sql
.\scripts\db-export.ps1 -Salida db\respaldo.sql
.\scripts\db-export.ps1 -SoloEsquema
```

Los scripts leen las credenciales de `.env`, no las imprimen y no incluyen `CREATE DATABASE`
(se puede restaurar en cualquier nombre con `-Base`).

## 5. Clave de firma JWT

```powershell
.\scripts\jwt-key-bootstrap.ps1
```

Genera `.secrets/jwt-signing-key.jwk` (ignorada por Git). Sin esta clave el backend no arranca:
`JWT_SIGN_KEY_LOCATION` debe apuntar al archivo.

## 6. Ejecutar

### 6.1 Backend nativo (desarrollo)

```powershell
$env:JAVA_HOME='C:\ruta\a\jdk-21'
cd backend
.\mvnw.cmd quarkus:dev
```

- API: `http://localhost:8088/api/v1`
- Health: `http://localhost:8088/q/health/ready`
- Swagger: `http://localhost:8088/q/swagger-ui`

### 6.2 Frontend en desarrollo

```powershell
cd frontend
npm ci
npm start     # http://localhost:4200 (proxy /api -> localhost:8088)
```

### 6.3 Docker

```powershell
# Modo B: frontend + backend; el backend usa el MySQL del host
docker compose up -d --build
# frontend http://localhost:8083 · backend http://localhost:8089

# Modo C: MySQL aislado (host 3309)
docker compose -f compose.yml -f compose.mysql.yml up -d --build
```

Requiere Docker Desktop en ejecucion. En Windows el contenedor llega al MySQL del host por
`host.docker.internal` (ya configurado en `.env.example` como `DOCKER_DB_HOST`).

## 7. Cuentas

| Perfil | Correo | Clave |
|---|---|---|
| Superadministrador dev | `elvis@canchadirecta.pe` | `12345678` |
| Cliente dev | `cliente.dev@canchadirecta.pe` | `12345678` |
| Propietario dev | `propietario.dev@canchadirecta.pe` | `12345678` |
| Operador dev | `operador.dev@canchadirecta.pe` | `12345678` |
| Cliente demo | `cliente@canchadirecta.pe` | `DemoCancha2026` |
| Propietario demo | `propietario@canchadirecta.pe` | `DemoCancha2026` |
| Operador demo | `operador@canchadirecta.pe` | `DemoCancha2026` |

Detalle y creacion de nuevas cuentas: [`CUENTAS_DESARROLLO.md`](CUENTAS_DESARROLLO.md) y
`scripts/usuario-dev.ps1`.

## 8. Verificar la instalacion

```powershell
# Backend
backend\mvnw.cmd -B -f backend\pom.xml test
powershell -File scripts\smoke.ps1 -BaseUrl http://localhost:8089

# Frontend
cd frontend
npm run build
npm test -- --watch=false
$env:BASE='http://localhost:8083'; npm run test:e2e
$env:BASE='http://localhost:8083'; $env:A11Y_SIN_SESION='1'; npm run test:a11y
```

Resultados esperados y detalle: [`PRUEBAS.md`](PRUEBAS.md) y [`RESULTADOS_PRUEBAS.md`](RESULTADOS_PRUEBAS.md).

## 9. Problemas comunes

| Sintoma | Causa | Solucion |
|---|---|---|
| `Angular requires Node >=22.22.3` | Node viejo | Instalar Node 24 LTS |
| `Could not expand value JWT_SIGN_KEY_LOCATION` | Falta la clave o la ruta | Ejecutar `jwt-key-bootstrap.ps1` y revisar `.env` |
| `Access denied for user ...` | `.env` incorrecto o usuarios no creados | `db-bootstrap-safe.ps1` |
| Backend no ve MySQL en Docker | `DB_HOST` del host | Usar `host.docker.internal` (Compose) o `compose.mysql.yml` |
| Puerto ocupado | 8088/8089/8083/3306 en uso | Cambiar los puertos en `.env` |
| Flyway no aplica cambios | `clean-disabled=true` (ADR-0007) | No usar `flyway clean`; recrear la base con `demo-reset.ps1 -Force` |
| Fechas desfasadas | Zona horaria | `APP_TIMEZONE=America/Lima`; ver `L-04` |

## 10. Estructura util

```
backend/                Quarkus + Flyway (migraciones V1..V18)
frontend/               Angular 22
db/canchadirecta.sql    volcado completo (esquema + datos)
scripts/
  db-bootstrap-safe.ps1 crea base y usuarios
  db-export.ps1         exporta a .sql
  db-import.ps1         restaura un .sql
  jwt-key-bootstrap.ps1 genera la clave JWT
  demo-reset.ps1        base de demostracion reproducible
  usuario-dev.ps1       cuentas de desarrollo con roles
docs/                   arquitectura, diagramas, pruebas, cuentas, limitaciones
```
