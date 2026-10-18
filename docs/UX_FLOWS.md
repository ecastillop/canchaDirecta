# UX Flows y sistema de diseño - CanchaDirecta

> Fuente: prompt maestro §09, mockups `02_Diseno/2.1_Prototipos_Mockups` (inventario de
> controles, no replica visual) y contratos reales del backend (`/q/openapi`).
> Estado: **diseno aprobado 2026-10-17**; implementacion pendiente (Fase 4).

## 1. Principios

1. **Profesional y deportivo, no academico**: densidad controlada, jerarquia clara, acento
   medido, sin degradados estridentes ni ilustraciones infantiles.
2. **Mobile-first real**: 360 px es el caso base; desktop agrega informacion, no la reordena.
3. **Toda accion visible tiene handler, estado y resultado** (exito, error, vacio, carga).
4. **Accesibilidad como requisito**: WCAG 2.2 AA en flujos principales, teclado y foco visibles.
5. **Parametrizable**: la identidad visual se cambia por tokens/paleta, no editando componentes.

## 2. Design system

### 2.1 Capas de tokens

`primitivos -> semanticos -> componente`. Los componentes solo consumen semanticos.

| Grupo | Tokens semanticos | Default (paleta "cancha") |
|---|---|---|
| Marca | `--cd-color-primary`, `--cd-color-primary-strong`, `--cd-color-accent` | azul marino `#0B2545` / `#123B66`, turquesa `#14B8A6` |
| Superficie | `--cd-color-bg`, `--cd-color-surface`, `--cd-color-surface-alt` | `#F6F8FB`, `#FFFFFF`, `#EEF2F7` |
| Texto | `--cd-color-text`, `--cd-color-text-muted`, `--cd-color-on-primary` | `#0F172A`, `#64748B`, `#FFFFFF` |
| Estado | `--cd-color-success`, `--cd-color-warning`, `--cd-color-danger`, `--cd-color-info` | `#15803D`, `#B45309`, `#B91C1C`, `#1D4ED8` |
| Borde | `--cd-color-border`, `--cd-color-focus` | `#D8E0EA`, `#14B8A6` |
| Espacio | `--cd-space-1..8` | 4, 8, 12, 16, 24, 32, 48, 64 px |
| Radio | `--cd-radius-sm/md/lg/pill` | 8, 12, 16, 999 px |
| Elevacion | `--cd-shadow-1/2/3` | sombras suaves azuladas |
| Tipografia | `--cd-font-sans`, `--cd-text-xs..3xl`, `--cd-leading-*` | Inter variable local + fallback sistema |
| Movimiento | `--cd-duration-fast/base`, `--cd-ease` | 120 ms / 200 ms, `cubic-bezier(.2,.8,.2,1)` |

### 2.2 Paletas intercambiables

- Paleta activa por `[data-palette="cancha"]` en `<html>` y variables CSS; el servicio
  `ThemeService` (signals) persiste la eleccion y aplica `data-palette` + `color-scheme`.
- Paletas previstas: `cancha` (default navy/turquesa), `grafito` (negro/lima), `arena`
  (verde/terracota). Solo cambian semanticos; ningun componente conoce hex.
- Dark mode queda como paleta adicional futura; no bloquea Fase 4.
- Contraste verificado por par texto/fondo (>= 4.5:1; >= 3:1 en texto grande y bordes de foco).

### 2.3 Tipografia, iconos e imagenes

- **Inter** self-hosted (woff2, variable) con `font-display: swap`; escala 12/14/16/20/24/30/36.
- Iconos SVG inline propios (stroke 1.75, 20/24 px), sin librerias de iconos completas.
- Imagenes: placeholders locales SVG con etiqueta ("Foto referencial"), sin fotos remotas ni
  marcas de terceros. `NgOptimizedImage`, `loading="lazy"`, relacion de aspecto fija.
- Graficos de ingresos: SVG/CSS propio (sin dependencia de charts salvo necesidad demostrada).

### 2.4 Componentes propios (comportamiento con Angular CDK)

| Componente | Base CDK / notas |
|---|---|
| `cd-button` | variantes primary/secondary/ghost/danger, loading y disabled reales |
| `cd-field` | label persistente, hint, error vinculado (`aria-describedby`), contador |
| `cd-select` | `cdk/listbox` + filtro; teclado completo |
| `cd-dialog` | `cdk/dialog` con focus trap, `aria-modal`, cierre por Escape |
| `cd-drawer` | `cdk/dialog` lateral en movil, foco atrapado |
| `cd-toast` | `cdk/overlay` + `aria-live="polite"` |
| `cd-slot-picker` | grid de franjas accesible (radio group), estados libre/ocupado/seleccionado |
| `cd-date-picker` | input nativo + validacion; rango de 365 dias de anticipacion |
| `cd-table` | desktop tabla; < 768 px tarjetas con labels (`data-label`) |
| `cd-pagination`, `cd-tabs`, `cd-badge`, `cd-avatar`, `cd-breadcrumb` | propios |
| `cd-skeleton`, `cd-empty-state`, `cd-error-state` | con accion de reintento |
| `cd-stat-card`, `cd-sidebar`, `cd-topbar` | layout por rol |
| `cd-stepper` | flujo de reserva (fecha -> resumen -> pago -> resultado) |
| `cd-confirm-dialog` | acciones destructivas con texto explicito |

Reglas: `OnPush` + signals, inputs/outputs tipados, sin logica de negocio en componentes,
estilos encapsulados y tokens; estados `loading|empty|error|success` documentados por componente.

## 3. Arquitectura frontend

```
src/app/
  core/        auth (session, guards, interceptor), http (error mapper, api base), config, theme
  shared/      ui/*, pipes, directives, layout (shell, sidebar, topbar)
  features/
    public/    home, catalogo, comparar, detalle
    auth/      login, registro, verificar, recuperar
    booking/   checkout, pago, resultado
    account/   mis-reservas, detalle, perfil, notificaciones, reclamos
    owner/     dashboard, establecimientos, canchas, agenda, cocheras, promociones,
               reservas-recibidas, ingresos, liquidaciones, solicitud
    admin/     usuarios, propietarios, supervision, pagos, reclamos, liquidaciones,
               auditoria, catalogos
  app.routes.ts (lazy por area), app.config.ts
```

- **Estado**: signals locales por feature; `AuthStore` y `ThemeStore` globales; sin NgRx.
- **HTTP**: servicios tipados por modulo (`AuthApi`, `CatalogoApi`, `ReservasApi`, ...),
  DTOs espejo de OpenAPI, `errorInterceptor` que traduce `code` del backend a mensajes ES,
  `authInterceptor` que adjunta el access token y refresca una vez ante `401` (excluyendo
  `auth/*`), `loadingInterceptor` para barra global.
- **Sesion**: access token **solo en memoria**; refresh token en `sessionStorage`; al recargar se
  intenta `POST /auth/refresh`; logout/rol revocado limpia ambos y redirige a login.
- **Errores**: mapa estable `400 VALIDATION_ERROR`, `401 UNAUTHENTICATED`, `403 FORBIDDEN`,
  `404 NOT_FOUND`, `409 CONFLICT/SLOT_UNAVAILABLE`, `429 TOO_MANY_ATTEMPTS`, `5xx`; nunca mostrar
  stack ni detalles internos.
- **Fechas/moneda**: `Intl` con `America/Lima` y `PEN`; el backend recibe/entrega ISO-8601 UTC.
- **Config**: `apiBaseUrl` por environment (`/api` en prod, proxy en dev); feature flags simples
  (`demoPago=true`, `verificacionCorreo`, `paletas`).

## 4. Rutas y flujos por actor

### Publico
| Ruta | Pantalla | API |
|---|---|---|
| `/` | Landing + buscador | `GET /canchas`, `/ubicaciones/distritos`, `/deportes` |
| `/canchas` | Catalogo con filtros en query params | `GET /canchas?...` |
| `/canchas/comparar?ids=` | Comparacion 2-4 | `GET /canchas/comparar?ids=` |
| `/canchas/:id` | Detalle, galería, reseñas, calendario semanal navegable (hora × día) y precio | `GET /canchas/{id}`, `/canchas/{id}/disponibilidad`, `/{id}/calificaciones` |
| `/auth/registro`, `/auth/login`, `/auth/verificar`, `/auth/recuperar` | Cuenta | `auth/*` |
| `**` | 404 con buscador | - |

### Usuario autenticado
| Ruta | Pantalla | API |
|---|---|---|
| `/reservar/:canchaId` | Stepper fecha -> resumen (cochera, promo, desglose) -> pago demo -> resultado | `POST /reservas` (+`Idempotency-Key`), `/pagos` |
| `/mis-reservas` | Tarjetas/tabla, filtros por estado/fecha | `GET /reservas/mias` |
| `/mis-reservas/:id` | Detalle, historial, cancelar, reprogramar, calificar, comprobante demo | `POST .../cancelar`, `/modificar`, calificaciones |
| `/reclamos`, `/reclamos/:id` | Reclamo y seguimiento | `engagement/*` |
| `/perfil`, `/notificaciones` | Perfil editable y avisos | `PUT /users/me`, `GET /notificaciones` |

### Propietario (`OWNER`)
Dashboard (reservas de hoy, ingresos, canchas activas), establecimientos (CRUD + baja logica),
canchas (fotos/deportes/caracteristicas), agenda (horarios semanales y bloqueos), cocheras,
promociones, reservas recibidas (confirmar/rechazar), ingresos/liquidaciones y solicitud de
validacion. Rutas bajo `/panel/*` con guard de rol.

### Administrador (`PLATFORM_ADMIN`)
`/admin/*`: usuarios/roles/estado, propietarios (validar/rechazar), supervision de
establecimientos/reservas/pagos, reclamos, liquidaciones, auditoria y catalogos. Nunca permite
editar auditoria ni elevar a `PLATFORM_ADMIN` desde UI publica. **El administrador no solicita
ser propietario**: si necesita publicar canchas propias se le asigna el rol `OWNER` (por otro
administrador o desde Usuarios → Roles); si solo supervisa, usa `/admin` → Supervision. El
cambio de roles se hace en un **modal** con foco atrapado, no en una fila inline.

### Listas extensas
Toda lista o tabla con mas de 10 elementos usa `cd-paginator` (catalogo 12). El paginador
muestra el rango visible, numeros con elipsis, `aria-current` y navegacion anterior/siguiente.
Aplica a panel, cuenta y administracion.

## 5. Estados y feedback (obligatorio en todas las areas)

- **Carga**: skeleton por bloque (no spinner de pagina completa salvo navegacion).
- **Vacio**: mensaje + accion util (ej. "Aun no tienes reservas" -> "Explorar canchas").
- **Error**: mensaje ES + boton "Reintentar"; conserva datos del formulario.
- **Exito**: toast `aria-live` + actualizacion optimista solo si es reversible.
- **Submit**: boton deshabilitado + `loading` hasta respuesta; sin doble envio.
- **Destructivo**: dialogo de confirmacion con consecuencia explicita.
- **Pago**: aviso permanente "Pago de demostracion"; nunca insinuar cobro real.

## 6. Responsive y layout

- Breakpoints: 360, 390, 768, 1024, 1440. Grid 4/8/12 columnas; contenedor max 1200 px.
- Navegacion: topbar + sidebar en >= 1024; drawer en < 1024; bottom actions en movil para
  checkout. Breadcrumb en detalle y paneles.
- Tablas: tarjetas con pares label/valor < 768 px; scroll horizontal controlado solo si es
  imprescindible (con `tabindex` y encabezados identificables).
- Filtros: sidebar en desktop, drawer/bottom-sheet en movil; estado en query params.

## 7. Accesibilidad (WCAG 2.2 AA)

- Skip link, landmarks, un solo `h1` por vista, jerarquia de headings.
- Foco visible con `--cd-color-focus`, orden logico, sin trampas; dialogs con CDK focus trap.
- Formularios: label persistente, `aria-invalid`, error asociado, mensajes en espanol.
- Slot picker operable con flechas/Enter, estado anunciado.
- Contraste AA, texto escalable 200 %, `prefers-reduced-motion`.
- Toasts y cambios asincronicos anunciados (`aria-live`); no depender solo del color.

## 8. Pruebas y evidencias

- Unitarias (Vitest) para servicios API, interceptors, guards, stores y pipes.
- Componentes criticos: formularios, slot picker, tablas adaptables, dialogo de confirmacion.
- `ng build` production sin warnings criticos; presupuestos de bundle.
- Capturas reales en `docs/evidencias/ui/{360,390,768,1024,1440}/` y checklist de teclado.
- E2E opcional (Playwright) para reserva completa contra backend real.

## 9. Orden de implementacion (slices verticales)

1. **Base**: scaffold, tokens/paletas, shell, componentes UI base, HTTP/auth/session, guards.
2. **Auth + cuenta**: registro, login, verificacion, recuperacion, perfil, notificaciones.
3. **Catalogo**: home, filtros, comparacion, detalle y disponibilidad.
4. **Reserva**: stepper, cochera/promo, pago demo, resultado, mis reservas y acciones.
5. **Propietario**: dashboard, venues, canchas, agenda, cocheras, promociones, recibidas,
   ingresos/liquidaciones y solicitud.
6. **Admin**: usuarios, propietarios, supervision, pagos, reclamos, auditoria y catalogos.
7. **Calidad**: responsive 5 anchos, a11y, estados completos, capturas y documentacion.

## 10. Decisiones aprobadas (2026-10-17)

- Componentes **propios** sobre Angular CDK; **no** Angular Material como capa visual.
- Identidad **parametrizable** por paletas/tokens; la paleta inicial es navy + turquesa y puede
  cambiarse sin tocar componentes.
- Sesion: **access en memoria + refresh en `sessionStorage`** con rotacion.
- Angular 22.2.3, TypeScript strict, SCSS, signals + RxJS, lazy por area.
- Dark mode, charts externos y redisenos por tenant quedan fuera de Fase 4.
