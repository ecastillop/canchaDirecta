# HANDOFF - CanchaDirecta

> Documento de continuidad. Permite retomar el proyecto **sin conversacion previa**.
> Actualizar tras cada hito significativo.

## 0. Mirada hacia adelante (corte 2026-10-18)

### Estado del corte

- Repositorio Git inicializado en `canchadirecta/`.
- Commits relevantes:
  - `70def9c` snapshot funcional previo al rediseño.
  - `592b527` rediseño de fusión (identidad, home, catálogo, detalle, login).
  - `c5cf2f8` favoritos, catálogo enriquecido y sidebars.
  - `8124fee` panel de cliente con sidebar.
  - `824ec14` topbar standalone, Actividades, Cómo funciona y sidebars definitivos.
- Stack operativo: frontend `http://localhost:8083`, backend `http://localhost:8089`,
  MySQL del host `127.0.0.1:3306` (el `compose.yml` usa `host.docker.internal`).
- Superadmin local: `admin@canchadirecta.pe`; clave en `.env` (`ADMIN_PASSWORD`).
- Pruebas: backend **162/162**, humo **153/153** (PowerShell) y **134/134** (Bash), unitarios **22/22**,
  E2E usuario **OK**, E2E propietario/admin **OK**, accesibilidad **34 rutas OK**.
- Ficha de espacio implementada (2026-10-18): `GET /api/v1/establecimientos/{id}/ficha`,
  ruta `/establecimientos/:id` y enlace desde el detalle de cancha.
- Actividades reales implementadas (2026-10-18): migración `V14`, API de actividades e
  inscripciones con cupos, panel del propietario `/panel/actividades` y
  `/mis-inscripciones` para el cliente.
- Cupones con límites implementados (2026-10-18): migración `V15`, límite total y por
  cliente en promociones, canje atómico al reservar, liberación al cancelar/rechazar/expirar
  y contador de canjes en el panel del propietario.
- Centro de ayuda implementado (2026-10-18): migración `V16`, API pública `GET /api/v1/ayuda`
  con búsqueda, página `/ayuda` con acordeones y enlaces desde topbar, cuenta y footer.
- Dashboard del propietario implementado (2026-10-18): `GET /api/v1/establecimientos/{id}/metricas`,
  página `/panel/metricas` con reservas recibidas, ingresos, ocupación, serie diaria, top de canchas
  y próximas reservas.
- Dashboard del cliente implementado (2026-10-18): `/mi-resumen` con próxima reserva, reservas
  activas, favoritos, notificaciones sin leer, pendientes e inscripciones próximas.
- Reportes de administración implementados (2026-10-18): `GET /api/v1/admin/reportes` y pestaña
  "Reportes" con usuarios, establecimientos, reservas, ingresos, series diarias, estados y top.
- Delta completo frente al standalone documentado en `docs/DELTA_STANDALONE.md`
  (2026-10-18): panel admin, propietario, cliente y orden de implementación.
- Bloque A del delta completado (2026-10-18): `/admin` convertido a portal con sidebar y topbar
  propias, rutas hijas (`propietarios`, `reclamos`, `usuarios`, `promociones`, `cocheras`,
  `supervision`, `reportes`, `auditoria`) y dashboard "Visión global" con KPIs, pendientes,
  accesos rápidos y últimas operaciones.
- Bloque B completado (2026-10-18): activar/desactivar sedes, página de Transacciones con
  comisión/neto, límites de canje en promociones admin, módulo de Actividades admin con
  cancelación, confirmación/rechazo de reservas desde administración y mesa de ayuda con
  creación de tickets.
- Alineación visual con el standalone (2026-10-18): portal a pantalla completa (sin topbar/footer
  públicos en panel y cuenta); sidebar de 252 px idéntica al prototipo (logo de barras, workspace
  label, iconos SVG, activo lima, tarjeta "Entorno de prueba" y cerrar sesión); topbar interno de
  78 px con breadcrumb, avisos y avatar; stat cards, accesos rápidos y dashboard intro.
- Bloque C completado (2026-10-18): migración `V17` con `configuracion_plataforma` (comisión,
  horas de reembolso y cancelación, anticipación máxima y correo de soporte);
  `GET/PUT /api/v1/admin/configuracion` con validación y auditoría; puerto
  `ConfiguracionPlataforma` leído en tiempo de ejecución por reservas, pagos y liquidaciones; página
  `/admin/configuracion` (ADR-0037).
- Bloque D completado (2026-10-18): rol `OPERADOR` y tabla `operador_establecimiento` (`V18`);
  paquete `reception` con `GET /api/v1/recepcion/{establecimientos,canchas,resumen,agenda,reservas,clientes,incidencias}`,
  reserva presencial, check-in, cobro e incidencias; `GET/POST/DELETE /api/v1/admin/establecimientos/{id}/operadores`;
  administración de operadores en `/admin/operadores` y portal `/recepcion` (Turno actual, Agenda del
  día, Reservas, Reserva presencial, Cobros e Incidencias) con `roleGuard` (ADR-0038).
- Badge de notificaciones sin leer (2026-10-18): `NotificacionesStore` compartido (`noLeidas`), badge
  en la campana de la topbar pública y de la cabecera de cuenta, y sincronización al marcar leídas
  desde `/notificaciones` (ADR-0039).
- Modo claro/oscuro explícito (2026-10-18): toggle en la topbar más selectores "Modo" en el menú y el
  pie, persistencia en `localStorage` (`cd.modo`) y arranque según `prefers-color-scheme`;
  `data-theme='dark'` aplica el juego oscuro de tokens sobre cualquier paleta y se migraron a tokens
  las superficies, bordes y textos fijos de los componentes de páginas y de los cuatro portales
  (ADR-0040).
- Calendario fiel a la agenda (2026-10-18): la rejilla "Disponibilidad semanal" se deriva del horario
  configurado, así que un bloqueo o una reserva se ven como "Sin cupo" en su hora (antes la hora
  desaparecía), los días sin agenda se rotulan "Cerrado" y se retiró el listado redundante "Horario
  semanal" de la ficha (ADR-0041).
- Bloqueos desde recepción (2026-10-18): el personal asignado a la sede puede bloquear y levantar
  franjas sin depender del propietario; la "Agenda del día" del portal de recepción suma la sección
  "Bloqueos de la fecha" (listado por sede y fecha, alta con cancha/desde/hasta/motivo y baja con
  confirmación) reutilizando `/canchas/{id}/bloqueos` (ADR-0042). Resuelve L-25.
- Ocupación semanal del propietario (2026-10-18): la pantalla "Agenda" del panel suma la sección
  "Ocupación de la semana" (rejilla de 7 días por las horas del horario configurado, con estados
  Libre/Reservada/Bloqueada/Ya pasó/Cerrado, leyenda y navegación de semana) alimentada por la
  bandeja de reservas recibidas con los filtros nuevos `idCancha`/`desde`/`hasta`; los ayudantes de
  calendario se unificaron en `shared/util/fecha-lima.ts` para la ficha pública y el panel (ADR-0043).
- Bandeja del propietario con el titular y su último pago (2026-10-18): `GET /reservas/recibidas`
  devuelve `cliente`, `telefono` y `pagoEstado` (estado del último pago) además de los campos de la
  reserva; la bandeja muestra "Cliente: nombre", el teléfono como enlace `tel:` y el distintivo del
  último pago (Pagado/Pago rechazado/Reembolsado/Pago pendiente/Sin pago), y conserva esos datos al
  confirmar, rechazar o revocar (ADR-0044).
- Reset de demo reproducible (2026-10-18): `scripts/demo-reset.ps1` respalda la base (`mysqldump` en
  `%TEMP%`), la recrea (Flyway migra al reiniciar el backend) y siembra por la API real el escenario
  de demostración: administrador, propietario validado con 4 sedes y 16 canchas (deportes,
  características, 2 fotos y horario de 7 días por cancha), operador asignado a la sede principal,
  cliente con dos reservas pagadas (una pendiente y una confirmada), un bloqueo de mantenimiento, un
  reclamo de soporte y una actividad pública (ADR-0045). Cuentas demo: `propietario@`, `operador@` y
  `cliente@canchadirecta.pe` (clave `DemoCancha2026`, ajustable con `-DemoPassword`).
- Tema dentro de los portales (2026-10-18): el botón de modo claro/oscuro (`CdThemeToggleComponent`)
  está en la cabecera de `panel`, `admin`, `recepcion` y `cuenta`, comparte `ThemeService`/`cd.modo`
  con el sitio y anuncia la acción por `aria-label`; se corrigieron los pares de color que el modo
  oscuro dejaba bajo 4.5:1 y `contraste-modos.mjs` lo verifica en los cuatro portales (ADR-0046).
- Cara pública de la agenda (2026-10-18): `GET /canchas/{id}/disponibilidad` devuelve `ocupadas[]` con
  el motivo de cada tramo (`RESERVADA`/`BLOQUEADA`, sin titular ni nota del bloqueo) y la ficha rotula
  "Reservada"/"Bloqueada" con su leyenda; los chips de día sin agenda dicen "Cerrado" (ADR-0047,
  L-26 resuelta).

### Ya integrado del standalone

- Identidad visual: bosque + lima, Outfit/DM Sans locales, home editorial, catálogo, detalle y login.
- Topbar: Inicio, Espacios, Actividades, Cómo funciona y menú “Mi cuenta” (sin selector “Vista demo”).
- Favoritos persistidos (`V12`, `/favoritos`), comparar como botón, reputación con número de opiniones,
  distrito, foto principal, iconos SVG y página de favoritos.
- Servicios incluidos en el detalle y datos demo (`V13`).
- Paneles con sidebar de cliente, propietario y administración (espacio de trabajo, ayuda y volver al sitio).
- `/actividades` ya opera con datos reales (listado, cupos e inscripción); `/como-funciona` sigue siendo informativa.
- Portal administrativo completo con Visión global, rutas hijas, módulos del Bloque B y
  configuración persistente de plataforma (Bloque C).
- Se conserva todo lo anterior: reservas, pagos demo, panel, administración, carrusel, calendario
  semanal, precios y comparación.

### Pendiente para la siguiente sesión (orden recomendado)

1. ~~Grupo de bloques A/B/C~~ (hecho 2026-10-18): portal admin, módulos reutilizando APIs y
   configuración persistente de plataforma.
2. ~~**Operador/Recepción** (Bloque D)~~ (hecho 2026-10-18): rol `OPERADOR`, turno, agenda de
   recepción, reserva presencial, check-in, cobros e incidencias (ADR-0038) y bloqueos de franja
   desde la agenda del día (ADR-0042). Pendiente menor del
   bloque: reserva telefónica sin cuenta registrada (L-24).
3. ~~**Notificaciones**: badge de no leídas en el topbar y campana~~ (hecho 2026-10-18, ADR-0039).
4. ~~**Modo oscuro** con toggle explícito y persistencia~~ (hecho 2026-10-18, ADR-0040).
5. ~~**Seed/reset de demo**~~ (hecho 2026-10-18, ADR-0045): `scripts/demo-reset.ps1` deja la base con
   un escenario controlado (4 sedes, 16 canchas, operador asignado a una sede, cliente y reservas).
6. ~~**Enlace de tema en los portales**~~ (hecho 2026-10-18, ADR-0046): botón de modo claro/oscuro en
   la cabecera de `panel`, `admin`, `recepcion` y `cuenta`, con contraste verificado en ambos modos.
7. ~~**Cara pública de la agenda**~~ (hecho 2026-10-18, ADR-0047): la disponibilidad informa
   `RESERVADA`/`BLOQUEADA` por tramo y la ficha la rotula, con "Cerrado" en los chips de día sin agenda.

### Reglas para continuar

- **No retirar funcionalidad existente**: la nueva navegación mueve accesos al menú “Mi cuenta”,
  pero no elimina rutas ni acciones.
- Mantener el lenguaje visual del standalone y sus sidebars.
- No incluir el selector “Vista demo” del standalone.
- Cada bloque nuevo debe pasar build, unitarios, E2E y a11y antes de commit.
- Mantener migraciones Flyway; no usar `drop-and-create` sobre la base real.

### Cómo retomar

```powershell
cd canchadirecta
docker compose -f compose.yml up -d --build backend frontend
.\scripts\demo-reset.ps1 -Force       # deja la base con el escenario de demostración
cd frontend
npm run build
npm test -- --watch=false
$env:BASE='http://localhost:8083'; npm run test:e2e
$env:BASE='http://localhost:8083'; $env:ADMIN_CORREO=...; $env:ADMIN_PASSWORD=...; npm run test:e2e:owner
npm run test:a11y
node e2e/contraste-modos.mjs          # contraste en los 4 portales, claro y oscuro
```

## 0.1 Estado global


- **Estado:** **BACKEND + FRONTEND OPERATIVOS**. Backend 162/162 tests y 153/153 humo;
  frontend Angular 22 compila, pasa 22/22 tests unitarios y 2 suites E2E reales (usuario y
  propietario/admin) contra backend, tanto en dev como en el contenedor Nginx (`8083`). Rediseño
  de fusión aplicado. Fase 0 y Fase 2 completadas. Modo claro/oscuro, badge de notificaciones,
  calendario fiel a la agenda, bloqueos desde recepción y ocupación semanal del propietario
  añadidos (ADR-0039, ADR-0040, ADR-0041, ADR-0042, ADR-0043, ADR-0044), la base de demostración
  se prepara con `scripts/demo-reset.ps1` (ADR-0045), el tema se controla dentro de los cuatro
  portales (ADR-0046) y la disponibilidad pública informa el motivo de cada franja ocupada
  (ADR-0047, L-26 resuelta).
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
- **Evidencia vigente (2026-10-18):** `mvnw test` -> **162/162 PASS** (`BUILD SUCCESS`); humo
  -> **153/153 PASS** en `smoke.ps1` y **134/134** en `smoke.sh` contra el contenedor
  (8089/3306, imagen reconstruida). CI en `.github/workflows/backend.yml` (MySQL 8.4 efimero).
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
  `npm run build` **OK**, `npm test` **22/22 PASS** y E2E Playwright **OK** en dev y en Docker
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
- **Ultima fecha de trabajo:** 2026-10-18
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
- Badge de notificaciones y modo claro/oscuro (ADR-0039/0040): 22/22 unitarios frontend, E2E usuario
  y propietario/admin OK, a11y 34 rutas OK; badge verificado E2E (2 -> 1 -> desaparece) y capturas
  del tema en `docs/evidencias/ui/{claro,oscuro}-*-1440.png` (`e2e/dark-mode-capturas.mjs`).
- Bloqueos de cancha desde recepcion (ADR-0042): `RecepcionTest` (el operador bloquea y levanta una
  franja de su sede; `403` en cancha de otra sede, al crear un horario y con un `USER` sin
  asignacion), E2E propietario/admin con el alta y la baja desde "Agenda del dia"
  (`docs/evidencias/ui/recepcion-bloqueo-1440.png`), backend 160/160, 22/22 unitarios y a11y 34
  rutas OK.
- Ocupacion semanal del propietario (ADR-0043): `ReservasTest` filtra la bandeja por cancha y por
  rango (y `400` con `desde` no ISO), E2E propietario/admin navega a la semana siguiente (horas
  "libre" y dias "cerrado") y vuelve a la actual (`docs/evidencias/ui/panel-agenda-1440.png`),
  backend 162/162, build OK, 22/22 unitarios, E2E usuario OK y a11y 34 rutas OK.
- Bandeja del propietario con titular y pago (ADR-0044): `ReservasTest` valida nombre, telefono y
  `pagoEstado` (nulo antes de pagar y `APROBADO` despues), E2E propietario/admin crea y paga una
  reserva por API, ve la franja "reservada" en la rejilla y al titular con "Pagado" en la bandeja
  (`docs/evidencias/ui/panel-agenda-1440.png` y `panel-reservas-1440.png`), backend 162/162, build OK,
  22/22 unitarios, E2E usuario OK y a11y 34 rutas OK.
- Reset de demo (ADR-0045): `scripts/demo-reset.ps1` corrio dos veces seguidas (idempotente) y dejo
  `usuarios=4 establecimientos=4 canchas=16 horarios=112 fotos=32 reservas=2 actividades=1`; sobre esa
  base pasaron backend 162/162, E2E usuario (pagina 2 del catalogo con 16 canchas), E2E
  propietario/admin, a11y 34 rutas y humo 153/153 PS y 134/134 Bash. Capturas del escenario limpio
  (`docs/evidencias/ui/demo-catalogo-1440.png` y `demo-ficha-1440.png`).
- Tema dentro de los portales (ADR-0046): 2/2 unitarios del componente, E2E usuario (cuenta a oscuro
  y de vuelta) y E2E propietario/admin (panel a oscuro, persiste al recargar y vuelve a claro),
  `contraste-modos.mjs` OK en 4 portales x 2 modos, a11y 34 rutas OK y humo 153/153 PS y 134/134 Bash;
  capturas `docs/evidencias/ui/panel-oscuro-1440.png` y `cuenta-oscuro-1440.png`.
- Motivo de la franja ocupada (ADR-0047): `ReservasTest` valida que la reserva y el bloqueo no se
  ofrecen y figuran como `RESERVADA`/`BLOQUEADA` en `ocupadas` (sin filtrar la nota del bloqueo),
  humo PS y Bash comprueban el estado, E2E propietario/admin ve ambas celdas en la rejilla y en la
  ficha publica (`docs/evidencias/ui/ficha-motivos-1440.png`), backend 163/163, 24/24 unitarios,
  a11y 34 rutas OK y contraste OK.
- `smoke.ps1` -> **153/153 PASS** (contenedor en 8089 con la imagen reconstruida y `-DbPort 3306`;
  incluye ficha, actividades, cupones, ayuda, reportes, operadores de recepción y el modulo de
  recepcion).
- `smoke.sh` -> **134/134 PASS** con Git Bash (`DB_PORT=3306 bash scripts/smoke.sh http://localhost:8089`).
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
   ADR-0001..**0047**, contratos API del frontend). Faltan diagramas finales y limpiar filas
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
duracion fija - ADR-0035 arquitectura del frontend y sistema de diseno - ADR-0036 calendario
semanal y ventana de 365 dias - ADR-0037 configuracion persistente de plataforma
(`configuracion_plataforma`, puerto `ConfiguracionPlataforma`, `GET/PUT /admin/configuracion`,
validacion y auditoria) - ADR-0038 rol `OPERADOR` y portal de recepcion (modulo `reception`, `V18` con
`operador_establecimiento`, alcance por sede, turno, agenda, reserva presencial, check-in, cobros e
incidencias) - ADR-0039 badge de notificaciones sin leer (`NotificacionesStore` compartido entre la
topbar, la cabecera de cuenta y `/notificaciones`) - ADR-0040 modo claro/oscuro explicito y persistente
(`cd.modo`, `data-theme='dark'`, `color-scheme` y juego oscuro de tokens) - ADR-0041 el calendario de la
ficha se deriva del horario configurado ("Sin cupo" en la hora ocupada o bloqueada y "Cerrado" en los
dias sin agenda) - ADR-0042 los bloqueos de la cancha los gestiona tambien el personal asignado a la
sede (`esGestorDeCancha`; los horarios y precios siguen siendo del propietario; L-25 resuelta) -
ADR-0043 ocupacion semanal del propietario (la bandeja de reservas recibidas acepta `idCancha`,
`desde` y `hasta` por solape sin ampliar el alcance del token, la pantalla "Agenda" suma la rejilla
de la semana derivada del horario con reservas y bloqueos, y los ayudantes de calendario se unifican
en `shared/util/fecha-lima.ts`; L-28 documenta el alcance de 60 min) - ADR-0044 la bandeja del
propietario muestra al titular y su ultimo pago (`GET /reservas/recibidas` agrega `cliente`,
`telefono` y `pagoEstado` con un DTO propio, separado de `/reservas/mias` para no exponer contacto al
titular; el puerto devuelve el read model `ReservaRecibida`) - ADR-0045 la base de demostracion se
prepara con `scripts/demo-reset.ps1` (respaldo, recreacion de la base, Flyway y siembra por la API de
4 sedes/16 canchas, operador asignado, cliente con reservas y una actividad; las cuentas demo se
documentan en el encabezado del script) - ADR-0046 el tema se controla tambien dentro de los portales
(`CdThemeToggleComponent` en las cuatro cabeceras, mismo `ThemeService`/`cd.modo`, y contraste
verificado en claro y oscuro por `frontend/e2e/contraste-modos.mjs`) - ADR-0047 la disponibilidad
publica informa el motivo de cada tramo ocupado (`ocupadas[]` con `RESERVADA`/`BLOQUEADA`, sin
titular ni nota del bloqueo; la ficha la rotula y los chips de dia sin agenda dicen "Cerrado";
L-26 resuelta).
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
| 2026-10-18 | Bloque C: configuración persistente de plataforma (`V17`, API, puerto compartido y UI) | **PASS** (backend **158/158**, humo **135/135** PS y **116/116** Bash, build OK, 22/22 unitarios, E2E usuario 20/20, propietario/admin 37/37, a11y 27 rutas OK; ADR-0037) |
| 2026-10-18 | Bloque D: rol `OPERADOR` y portal de recepción (`V18`, paquete `reception`, `/recepcion`, `/admin/operadores`) | **PASS** (backend **160/160** (`RecepcionTest` 2/2), humo **153/153** PS y **134/134** Bash, build OK, 22/22 unitarios, E2E usuario OK, propietario/admin OK, a11y **34 rutas OK**; ADR-0038; capturas `recepcion-*.png` y `admin-operadores-1440.png`) |
| 2026-10-18 | Badge de notificaciones sin leer + modo claro/oscuro (`NotificacionesStore`, `data-theme`, tokens oscuros y migración de colores fijos) | **PASS** (build OK, 22/22 unitarios, E2E usuario OK, propietario/admin OK, a11y **34 rutas OK**; badge verificado E2E: 2 → marcar una → 1 → marcar todas → desaparece; capturas `claro-*-1440.png` y `oscuro-*-1440.png`; ADR-0039/0040) |
| 2026-10-18 | Calendario derivado del horario configurado y bloqueos visibles (`cancha-detalle`: filas del horario, "Sin cupo" en la hora bloqueada, "Cerrado" en días sin agenda, sin listado "Horario semanal") | **PASS** (datos reales: bloqueo 10:00-11:00 → API 7→6 franjas, la rejilla mantiene la fila 10:00 como "Sin cupo" y 6 libres; días sin horario "Cerrado"; build OK, 22/22 unitarios, E2E usuario OK, propietario/admin OK, a11y **34 rutas OK**; ADR-0041) |
| 2026-10-18 | Bloqueos de cancha desde recepción (`VenuesRepositorio.esGestorDeCancha`, sección "Bloqueos de la fecha" en la agenda del portal) | **PASS** (backend **160/160** (`RecepcionTest` 2/2 con el alta y la baja del operador y los `403` de cancha ajena, horarios y usuario sin asignación), humo **153/153** PS y **134/134** Bash, build OK, 22/22 unitarios, E2E usuario OK, propietario/admin OK con alta y baja desde la UI, a11y **34 rutas OK**; ADR-0042; captura `recepcion-bloqueo-1440.png`) |
| 2026-10-18 | Bandeja del propietario con titular y último pago (`/reservas/recibidas` con `cliente`/`telefono`/`pagoEstado`, DTO propio separado de `/reservas/mias`; bandeja con enlace `tel:` y distintivo de pago) | **PASS** (backend **162/162** con `laBandejaDelPropietarioMuestraAlTitularYSuUltimoPago`, humo **153/153** PS y **134/134** Bash, build OK, 22/22 unitarios, E2E usuario OK, propietario/admin OK con reserva y pago por API, rejilla "reservada", titular y "Pagado" en la bandeja, a11y **34 rutas OK**; ADR-0044; capturas `panel-agenda-1440.png` y `panel-reservas-1440.png`) |

| 2026-10-18 | Reset de demo (`scripts/demo-reset.ps1`: respaldo, base recreada + Flyway y siembra por API de admin/propietario/operador/cliente, 4 sedes, 16 canchas, reservas y actividad) | **PASS** (dos ejecuciones seguidas; `usuarios=4 establecimientos=4 canchas=16 horarios=112 fotos=32 reservas=2 actividades=1`; sobre esa base: backend **162/162**, E2E usuario OK (catálogo con 16 canchas y 2 páginas), E2E propietario/admin OK, a11y **34 rutas OK**, humo **153/153** PS y **134/134** Bash; ADR-0045; capturas `demo-catalogo-1440.png` y `demo-ficha-1440.png`) |

| 2026-10-18 | Tema dentro de los portales (`CdThemeToggleComponent` en panel/admin/recepción/cuenta, iconos `sun`/`moon`, `--cd-color-on-danger`/`on-accent` y ajustes de contraste) | **PASS** (24/24 unitarios, 2 nuevos; E2E usuario OK con cambio de tema en la cuenta; E2E propietario/admin OK con persistencia al recargar; `contraste-modos.mjs` OK en **4 portales × 2 modos**; a11y **34 rutas OK**; humo **153/153** PS y **134/134** Bash; ADR-0046; capturas `panel-oscuro-1440.png` y `cuenta-oscuro-1440.png`) |

| 2026-10-18 | Motivo de la franja ocupada (`disponibilidad.ocupadas[]` con `RESERVADA`/`BLOQUEADA`; ficha con "Reservada"/"Bloqueada" y chips "Cerrado") | **PASS** (backend **163/163** con `laDisponibilidadDistingueReservaYBloqueo`; humo **153/153** PS y **134/134** Bash con el estado en `ocupadas`; E2E usuario OK; E2E propietario/admin OK con celdas reservada/bloqueada en la rejilla y en la ficha pública; a11y **34 rutas OK**; contraste OK; ADR-0047; captura `ficha-motivos-1440.png`; L-26 resuelta) |

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

La hoja de ruta vigente esta en **`## 0. Mirada hacia adelante`** al inicio de este documento.
Prioridad inmediata:

1. Pulido de a11y manual (teclado/contraste, L-23) y diagramas finales.

**Estado:** entrega base cerrada el 2026-10-17; rediseno de fusion, integraciones del standalone y
bloques A/B/C/D del delta completados el 2026-10-18, más badge de notificaciones, modo claro/oscuro,
calendario fiel a la agenda, bloqueos desde recepción, ocupación semanal del propietario, la bandeja
del propietario con titular y pago, el tema dentro de los cuatro portales, la disponibilidad pública
con motivo por franja y la base de demostración regenerable con `.\scripts\demo-reset.ps1 -Force`.
Acta: `docs/ACTA_CIERRE.md`.

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
- Humo: usar el MySQL del host (`scripts/smoke.ps1 -BaseUrl http://localhost:8089 -DbPort 3306`, o
  dejar que lea `DB_PORT` de `.env`). El contenedor `canchadirecta-mysql-1` (3309) es **otra** base:
  con 3309 los pasos que leen/escriben la BD fallan (`403` en `/admin/*`, reserva sin finalizar).
  `mvnw test`, el humo y los E2E comparten la BD (Modo B): **no ejecutarlos en paralelo**.
- `GET /establecimientos/mios` **ya existe** y el panel descubre el local automáticamente.
  Para precargar la edición también está `GET /establecimientos/{id}` (solo propietario). Las canchas
  propias se listan con `GET /canchas?idEstablecimiento=...`.
- **Detalle de cancha rediseñado**: galería con fallback, bloques de información, reserva sticky
  y calendario semanal dentro de la columna principal; en móvil la reserva va primero. El botón
  `Reservar` y la paginación ahora respetan `disabled` (antes la directiva `cdButton` lo borraba).
- **Ventana de reserva**: la configuracion persistente (`configuracion_plataforma`, editable en
  `/admin/configuracion`) define los dias de anticipacion; `APP_CONFIG.diasAnticipacionMax` del
  frontend conserva 365 como limite inicial del calendario (ADR-0037).

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
