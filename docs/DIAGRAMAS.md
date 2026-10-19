# Diagramas de arquitectura

Fuentes Mermaid de los diagramas del acta. Se renderizan en GitHub, GitLab, VS Code
(extension Markdown Preview Mermaid) y en <https://mermaid.live>. Las imagenes exportadas
para el documento final viven en `docs/evidencias/diagramas/`.

Convenciones: el sentido de las flechas es "depende de" o "llama a"; los puertos son los del
`compose.yml`; los nombres de tablas y modulos coinciden con el codigo.

## 1. Contexto (C4 nivel 1)

```mermaid
flowchart LR
  cliente["Cliente<br/>busca y reserva"]
  propietario["Propietario<br/>gestiona sus canchas"]
  operador["Operador de recepcion<br/>atiende el mostrador"]
  admin["Administrador<br/>PLATFORM_ADMIN"]

  subgraph cd["CanchaDirecta"]
    spa["Frontend Angular 22<br/>SPA + design tokens"]
    api["Backend Quarkus<br/>REST /api/v1"]
    db[("MySQL 8<br/>Flyway")]
  end

  correo["Correo saliente<br/>(simulado en demo)"]
  pasarela["Pasarela de pago<br/>simulada y determinista"]

  cliente --> spa
  propietario --> spa
  operador --> spa
  admin --> spa
  spa -->|"HTTPS/JSON"| api
  api --> db
  api --> correo
  api --> pasarela
```

![Diagrama de contexto](evidencias/diagramas/01-contexto.png)

## 2. Contenedores y despliegue (Compose)

```mermaid
flowchart TB
  navegador["Navegador<br/>:8083"]

  subgraph compose["docker compose (red cd-net)"]
    frontend["frontend<br/>Nginx + SPA<br/>:80"]
    backend["backend<br/>Quarkus :8080<br/>healthcheck /q/health/ready"]
  end

  jwk[".secrets/jwt-signing-key.jwk<br/>montado :ro"]
  mysql[("MySQL 8<br/>host :3306 o compose.mysql :3309")]

  navegador -->|"GET /"| frontend
  frontend -->|"proxy /api"| backend
  backend -->|"DB_HOST / DB_PORT"| mysql
  backend -.->|"firma JWT"| jwk
  backend -->|":8089 publicado<br/>diagnostico"| navegador
```

![Diagrama de despliegue](evidencias/diagramas/02-despliegue.png)

Modos soportados: backend nativo contra el MySQL del host, Compose contra el MySQL del host
(`host.docker.internal`) y Compose con MySQL aislado (`compose.mysql.yml`).

## 3. Modulos del backend (monolito modular + hexagonal)

```mermaid
flowchart LR
  subgraph entrada["adapter/in"]
    rest["REST /api/v1<br/>Problem Details RFC 9457"]
  end

  subgraph modulos["Modulos de negocio"]
    identity["identity"]
    venues["venues<br/>incluye catalogo y ubigeo"]
    bookings["bookings"]
    payments["payments"]
    settlements["settlements"]
    engagement["engagement"]
    activities["activities"]
    reception["reception"]
    dashboard["dashboard"]
    helpcenter["helpcenter"]
    platform["platform<br/>admin global"]
  end

  subgraph salida["adapter/out"]
    persistencia["persistencia JPA"]
    externos["pago simulado · correo · storage"]
  end

  db[("MySQL 8")]

  rest --> identity & venues & bookings & payments & settlements
  rest --> engagement & activities & reception & dashboard & helpcenter & platform
  modulos --> salida
  persistencia --> db
```

![Diagrama de modulos](evidencias/diagramas/03-modulos.png)

Regla de dependencias: `domain` no conoce Quarkus, HTTP ni JPA; los puertos miran hacia dentro y
los adaptadores los implementan. `shared` concentra seguridad JWT, reloj, errores y utilidades.

## 4. Secuencia: reserva concurrente (ADR-0005)

```mermaid
sequenceDiagram
  autonumber
  participant U as Cliente
  participant SPA as Frontend
  participant API as ReservaResource
  participant UC as CrearReservaService
  participant DB as MySQL
  participant PG as Pasarela simulada

  U->>SPA: elige cancha, fecha y franja
  SPA->>API: POST /api/v1/reservas (Idempotency-Key)
  API->>UC: crear(solicitud)
  UC->>DB: BEGIN
  UC->>DB: SELECT cancha ... FOR UPDATE
  Note over UC,DB: serializa por recurso y valida horario, bloqueos y solapes
  UC->>DB: INSERT reserva (PENDIENTE) + historial + idempotencia
  UC->>DB: COMMIT
  UC->>PG: cobro simulado (fuera del lock)
  PG-->>UC: APROBADO / RECHAZADO
  UC->>DB: estado CONFIRMADA / CANCELADA + notificacion
  UC-->>API: reserva
  API-->>SPA: 201 Created
  SPA-->>U: confirmacion y proxima reserva
```

![Secuencia de la reserva concurrente](evidencias/diagramas/04-reserva-concurrente.png)

Si el pago no llega dentro del hold, el job de expiracion pasa la reserva a `EXPIRADA` y libera
la franja (ADR-0022).

## 5. Estados de la reserva

```mermaid
stateDiagram-v2
  [*] --> PENDIENTE: se crea la reserva
  PENDIENTE --> CONFIRMADA: pago aprobado o confirmacion del propietario
  PENDIENTE --> EXPIRADA: vence el hold de pago
  PENDIENTE --> CANCELADA: cancela el titular
  CONFIRMADA --> FINALIZADA: job de cierre (fin < ahora)
  CONFIRMADA --> CANCELADA: cancelacion del titular
  CONFIRMADA --> RECHAZADA: rechazo o revocacion del propietario
  CANCELADA --> [*]
  RECHAZADA --> [*]
  EXPIRADA --> [*]
  FINALIZADA --> [*]

  note right of CANCELADA
    Con pago APROBADO y politica aplicable
    se registra reembolso PROCESADO
  end note
```

![Estados de la reserva](evidencias/diagramas/05-estados-reserva.png)

## 6. Portales del frontend

```mermaid
flowchart TB
  shell["ShellComponent<br/>cabecera publica + skip link + foco al contenido"]
  auth["AuthLayout<br/>/auth/*"]
  cuenta["Cuenta<br/>/mi-resumen · /mis-reservas · /mis-inscripciones<br/>/favoritos · /notificaciones · /reclamos · /perfil"]
  panel["Panel del propietario<br/>/panel · métricas · reservas · agenda<br/>promociones · liquidaciones · actividades"]
  recepcion["Recepción<br/>/recepcion · agenda · reservas · nueva<br/>cobros · incidencias"]
  admin["Administración<br/>/admin · usuarios · transacciones · actividades<br/>reportes · configuración · operadores"]

  shell --> cuenta
  shell --> panel
  shell --> recepcion
  shell --> admin
  shell -.->|"rutas sin sesion"| auth
```

![Portales del frontend](evidencias/diagramas/06-portales-frontend.png)

Los guards son apoyo de UX: la autorizacion real vive en el backend (JWT + roles). Todas las
areas comparten estados de carga, vacio, error y exito, y el tema claro/oscuro se controla dentro
de cada portal.

## 7. Modelo de datos principal (ER simplificado)

```mermaid
erDiagram
  USUARIO ||--o{ USUARIO_ROL : tiene
  ROL ||--o{ USUARIO_ROL : asigna
  USUARIO ||--o| PROPIETARIO : solicita
  PROPIETARIO ||--o{ ESTABLECIMIENTO : posee
  ESTABLECIMIENTO ||--o{ CANCHA : contiene
  ESTABLECIMIENTO ||--o{ COCHERA : ofrece
  ESTABLECIMIENTO ||--o{ PROMOCION : publica
  CANCHA ||--o{ HORARIO_CANCHA : agenda
  CANCHA ||--o{ BLOQUEO_CANCHA : bloquea
  CANCHA ||--o{ FOTO_CANCHA : muestra
  USUARIO ||--o{ RESERVA : realiza
  CANCHA ||--o{ RESERVA : recibe
  RESERVA ||--o{ HISTORIAL_RESERVA : audita
  RESERVA ||--o| PAGO : cobra
  PAGO ||--o| REEMBOLSO : devuelve
  RESERVA ||--o| CALIFICACION : evalua
  PROPIETARIO ||--o{ LIQUIDACION : cobra
  LIQUIDACION ||--o{ DETALLE_LIQUIDACION : detalla
  RESERVA ||--o| DETALLE_LIQUIDACION : liquida
  USUARIO ||--o{ RECLAMO : reclama
  USUARIO ||--o{ NOTIFICACION : recibe
  ESTABLECIMIENTO ||--o{ ACTIVIDAD : programa
  ACTIVIDAD ||--o{ ACTIVIDAD_INSCRIPCION : inscribe
  USUARIO ||--o{ ACTIVIDAD_INSCRIPCION : participa
```

![Modelo de datos principal](evidencias/diagramas/07-modelo-datos.png)

El detalle de columnas, migraciones y restricciones esta en `MODELO_DATOS.md`; el esquema real lo
posee Flyway (V1..V18) e Hibernate solo valida.
