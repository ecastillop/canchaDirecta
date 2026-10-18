# Delta entre el standalone y la app actual

Fecha: 2026-10-18. Comparacion entre `CanchaDirecta_STANDALONE.html` y la app Angular/Quarkus.
Prioridad: primero panel administrativo, luego modulos de propietario y cliente.

## 1. Resumen ejecutivo

El standalone tiene cuatro portales completos: cliente, propietario, recepcion (operador) y
administrador. La app actual cubre cliente y propietario con backend real, y administracion
parcial. Las mayores brechas son:

1. **El panel administrativo no usa el portal visual del standalone** (sidebar oscura, topbar
   interno, dashboard de inicio); sigue con pestanas dentro del shell publico.
2. **Falta la "Vision global" del administrador** como landing: KPIs, aprobaciones pendientes,
   ultimas operaciones y accesos rapidos. Hoy "Reportes" solo muestra analitica.
3. **Faltan modulos administrativos**: Actividades, Transacciones como pagina propia y
   Configuracion de plataforma persistente.
4. **El rol Operador/Recepcion no existe** en la app (solo USER, OWNER, PLATFORM_ADMIN).
5. **Propietario y cliente tienen funcionalidad equivalente, pero no la misma navegacion** del
   standalone (sedes, espacios, tarifas, personal, billetera).

## 2. Panel administrativo: pagina por pagina

| Standalone | Nuestra app | Estado | Delta principal |
|---|---|---|---|
| Vision global (`resumen`) | Pestana Reportes | Parcial | No hay landing con KPIs, aprobaciones pendientes ni ultimas operaciones. |
| Sedes y aprobaciones | Supervision > Establecimientos | Parcial | Solo lectura; faltan acciones de aprobar/suspender/editar sede. |
| Usuarios y permisos | Pestana Usuarios | Casi completo | Falta edicion de perfil; roles y estado ya existen. |
| Todas las reservas | Supervision > Reservas | Parcial | Solo lectura; faltan confirmar/rechazar/check-in global. |
| Transacciones y comisiones | Supervision > Pagos + Reportes | Parcial | No hay pagina propia con bruto, comision y neto por operacion. |
| Promociones y cupones | Pestana Promociones | Parcial | No expone en el formulario admin los limites de canje ya soportados. |
| Actividades | No existe | Faltante | Sin supervision ni alta de actividades desde admin. |
| Mesa de ayuda | Pestana Reclamos | Parcial | Existe moderacion y seguimientos; falta vista tipo help desk y nuevo ticket. |
| Auditoria | Pestana Auditoria | Completo | Sin cambios funcionales. |
| Configuracion general | No existe | Faltante | No hay tabla/API de parametros ni UI. |

### Detalle tecnico

- Frontend admin actual: `features/admin/admin.component.ts` con pestanas
  `propietarios`, `reclamos`, `usuarios`, `promociones`, `cocheras`, `supervision`,
  `reportes`, `auditoria`.
- API admin actual (`core/api/admin.api.ts`): propietarios, reclamos, usuarios, establecimientos,
  reservas, pagos, promociones, cocheras, auditoria y reportes.
- Backend actual (`platform/adapter/in/rest`): recursos equivalentes a esas pestanas.
- **No existen** endpoints de:
  - `/api/v1/admin/actividades`
  - `/api/v1/admin/transacciones`
  - `/api/v1/admin/configuracion`
  - cambios de estado de establecimientos
  - acciones administrativas sobre reservas
- Roles actuales: `USER`, `OWNER`, `PLATFORM_ADMIN`. No hay `OPERADOR`.

## 3. Brecha visual del panel admin

Estado actual: shell publico (topbar + footer) + navegacion horizontal de pestanas.
Standalone: portal a pantalla completa con:

- Sidebar de 252 px, fondo `#121f21`, logo de barras lima, workspace label, iconos SVG,
  item activo `#284638` con barra lima y tarjeta "Entorno de prueba".
- Topbar interna de 78 px con breadcrumb, titulo de pagina, avisos y avatar.
- Contenido con dashboard intro, stat cards de 4 columnas y paneles con cabecera.
- Breadcrumb/titulo por pagina (`Vision global`, `Sedes y aprobaciones`, etc.).

Nuestra app ya tiene ese lenguaje visual en `/panel` y `/mi-resumen`, pero **no en `/admin`**.
La solucion recomendada es reutilizar el mismo componente de portal/sidebar y convertir las
pestanas en rutas hijas del admin.

## 4. Delta de propietario

| Standalone | Nuestra app | Estado |
|---|---|---|
| Vista general | `/panel` | Parcial: gestion de establecimiento, sin KPIs en la landing. |
| Agenda | `/panel/agenda` | Completo funcionalmente. |
| Reservas | `/panel/reservas` | Completo. |
| Mis sedes | Integrado en `/panel` | Parcial: sin pagina dedicada ni estado de aprobacion. |
| Espacios | Integrado en `/panel` | Parcial: sin listado dedicado con filtros. |
| Bloqueos | `/panel/agenda` | Completo. |
| Tarifas | Edicion de cancha | Parcial: no hay pagina de tarifas ni ajustes fin de semana/nocturno. |
| Actividades | `/panel/actividades` | Completo. |
| Finanzas | `/panel/liquidaciones` | Parcial: falta desglose comision/neto tipo standalone. |
| Personal | No existe | Faltante. |
| Configuracion del negocio | No existe | Faltante. |
| Metricas | `/panel/metricas` | Extra ya implementado, no presente en el standalone. |

## 5. Delta de cliente

| Standalone | Nuestra app | Estado |
|---|---|---|
| Mi resumen | `/mi-resumen` | Equivalente (mejorado con datos reales). |
| Mis reservas | `/mis-reservas` | Completo. |
| Favoritos | `/favoritos` | Completo. |
| Actividades | `/actividades` + `/mis-inscripciones` | Completo, pero separado del portal de cuenta como en standalone. |
| Pagos y saldo | No existe | Faltante: el historial solo aparece dentro de cada reserva. |
| Notificaciones | `/notificaciones` | Completo. |
| Soporte | `/ayuda` + `/reclamos` | Completo (centro de ayuda + tickets). |
| Mi perfil | `/perfil` | Completo. |

## 6. Orden recomendado de implementacion

### Bloque A - Shell administrativo + Vision global (P0) - HECHO 2026-10-18
- Reutilizar el portal (sidebar oscura + topbar) para `/admin`.
- Convertir pestanas en rutas: `/admin`, `/admin/sedes`, `/admin/usuarios`,
  `/admin/reservas`, `/admin/transacciones`, `/admin/cupones`, `/admin/actividades`,
  `/admin/soporte`, `/admin/auditoria`, `/admin/configuracion`.
- Landing `/admin` con KPIs, aprobaciones pendientes y ultimas operaciones.
- Backend: endpoint de resumen admin o composicion con endpoints existentes.
- Impacto alto, riesgo bajo; no requiere cambios de esquema.
- Implementado: `admin-layout.component.ts`, `admin-dashboard.component.ts`, rutas hijas y
  acceso al portal admin desde el shell. Validado con E2E y accesibilidad.

### Bloque B - Modulos admin reutilizando APIs (P1) - HECHO 2026-10-18
- Sedes con acciones (usar validacion de propietario y estado de establecimiento).
- Reservas con acciones de confirmar/rechazar globales.
- Transacciones con bruto/comision/neto a partir de pagos.
- Cupones: exponer limites de canje en el alta admin y contador de usos.
- Actividades: listado y cancelacion desde admin.
- Mesa de ayuda: reutilizar reclamos con filtros y respuesta rapida.

Hecho: activar/desactivar sedes (`POST /admin/establecimientos/{id}/estado`), Transacciones,
limites de canje en promociones admin, Actividades admin (`GET/DELETE /admin/actividades`),
confirmar/rechazar reservas (`POST /admin/reservas/{id}/confirmar|rechazar`) y mesa de ayuda
con nuevo ticket (`POST /admin/reclamos`).

### Bloque C - Configuracion persistente (P2) - HECHO 2026-10-18
- Migracion `configuracion_plataforma` (comision, horas de cancelacion, dias de anticipacion,
  correo de soporte).
- API `GET/PUT /api/v1/admin/configuracion`.
- Reemplazar `@ConfigProperty` por lectura de configuracion donde corresponda.
- UI de configuracion.

Hecho: `V17` crea `configuracion_plataforma` con cinco claves (comision, horas de cancelacion
gratuita, horas minimas de cancelacion, dias de anticipacion y correo de soporte). El puerto
`ConfiguracionPlataforma` se lee en cada operacion desde reservas, pagos y liquidaciones; la API
`GET/PUT /api/v1/admin/configuracion` valida rangos, audita y la ruta `/admin/configuracion`
permite editarla. Ver ADR-0037.

### Bloque D - Operador / recepcion (P3) - HECHO 2026-10-18
- Nuevo rol `OPERADOR` y permisos por establecimiento.
- Portal de recepcion: turno, agenda, reserva presencial, cobros, incidencias.
- Es el bloque mas grande y requiere modelo de personal/permisos.

Hecho: `V18` agrega el rol `OPERADOR` y la tabla `operador_establecimiento`; el alcance por sede se
resuelve en SQL (ADMIN = todas, OPERADOR = asignadas activas, OWNER = propias) y el paquete
`reception` expone `GET /api/v1/recepcion/{establecimientos,canchas,resumen,agenda,reservas,clientes,incidencias}`,
`POST /api/v1/recepcion/reservas` (walk-in), `POST .../reservas/{id}/checkin`,
`POST .../reservas/{id}/cobro` e incidencias (que reutilizan la tabla `reclamo`). La administracion
vincula personal con `GET/POST/DELETE /api/v1/admin/establecimientos/{id}/operadores[/{idUsuario}]`
y la UI vive en `/admin/operadores`; el portal `/recepcion` cubre Turno actual, Agenda del dia,
Reservas, Reserva presencial, Cobros e Incidencias. Fuera del bloque: bloqueos desde recepcion y
reserva telefonica sin cuenta registrada (L-24, L-25). Ver ADR-0038.

### Bloque E - Completar propietario y cliente (P4)
- Cliente: pagina de pagos y saldo.
- Propietario: paginas dedicadas de sedes, espacios, tarifas y finanzas; personal y
  configuracion del negocio.

## 7. Criterio de cierre por bloque

Cada bloque debe pasar:

- `backend\mvnw.cmd -B test` sin fallos.
- `scripts\smoke.ps1` contra Docker (8089).
- `npm run build`, `npx ng test --watch=false`.
- `npm run test:e2e`, `npm run test:e2e:owner`, `npm run test:a11y`.
- Capturas en `docs/evidencias/ui/` y HANDOFF actualizado.
